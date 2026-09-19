package ru.coordination.approval.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.audit.AuditEvent;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.statemachine.TransitionConfig;
import ru.coordination.approval.domain.statemachine.TriggerType;
import ru.coordination.approval.engine.exception.InvalidTargetStatusException;
import ru.coordination.approval.engine.exception.NoApplicableTransitionException;
import ru.coordination.approval.engine.model.ModelFactory;
import ru.coordination.approval.engine.model.TransitionKey;
import ru.coordination.approval.engine.model.TransitionModel;
import ru.coordination.approval.engine.registry.Action;
import ru.coordination.approval.engine.registry.ActionRegistry;
import ru.coordination.approval.engine.registry.Guard;
import ru.coordination.approval.engine.registry.GuardRegistry;

/**
 * Юнит-тесты {@link TransitionEngine} на in-memory/мок-модели, без БД (PHASE-02, T5) —
 * покрывают критерии приёмки 1–4 тикета.
 */
@ExtendWith(MockitoExtension.class)
class TransitionEngineUnitTest {

    private static final EntityType ENTITY_TYPE = EntityType.PROCESS;
    private static final UUID CONFIG_ID = UUID.randomUUID();
    private static final UUID ENTITY_ID = UUID.randomUUID();
    private static final String FROM_STATE = "DRAFT";

    @Mock
    private ModelFactory modelFactory;
    @Mock
    private GuardRegistry guardRegistry;
    @Mock
    private ActionRegistry actionRegistry;
    @Mock
    private StatusRegistryRepository statusRegistryRepository;
    @Mock
    private AuditEventRepository auditEventRepository;

    @InjectMocks
    private TransitionEngine engine;

    private final AtomicReference<String> writtenStatus = new AtomicReference<>();

    @BeforeEach
    void resetWrittenStatus() {
        writtenStatus.set(null);
    }

    @Test
    void picksFirstByPriorityWhoseGuardsAllPass() {
        TransitionConfig blockedHighPriority = transition("T_HIGH", "REVIEW", TriggerType.USER_ACTION, List.of("BLOCK"));
        TransitionConfig allowedLowPriority = transition("T_LOW", "REJECTED", TriggerType.USER_ACTION, List.of("PASS"));

        stubModel(List.of(blockedHighPriority, allowedLowPriority));
        when(guardRegistry.resolve("BLOCK")).thenReturn(alwaysFalse());
        when(guardRegistry.resolve("PASS")).thenReturn(alwaysTrue());
        when(statusRegistryRepository.existsByCodeAndEntityType("REJECTED", ENTITY_TYPE.name())).thenReturn(true);

        TransitionResult result = transition(TriggerType.USER_ACTION);

        assertThat(result.performed()).isTrue();
        assertThat(result.transitionCode()).isEqualTo("T_LOW");
        assertThat(result.toState()).isEqualTo("REJECTED");
        assertThat(writtenStatus.get()).isEqualTo("REJECTED");
    }

    @Test
    void successfulTransitionRunsActionsInOrderAndRecordsAudit() {
        TransitionConfig config = transition(
                "T_APPROVE", "APPROVED", TriggerType.USER_ACTION, List.of("G"), List.of("A1", "A2"), List.of("EVT1", "EVT2"));
        stubModel(List.of(config));
        when(guardRegistry.resolve("G")).thenReturn(alwaysTrue());
        Action action1 = Mockito.mock(Action.class);
        Action action2 = Mockito.mock(Action.class);
        when(actionRegistry.resolve("A1")).thenReturn(action1);
        when(actionRegistry.resolve("A2")).thenReturn(action2);
        when(statusRegistryRepository.existsByCodeAndEntityType("APPROVED", ENTITY_TYPE.name())).thenReturn(true);
        when(auditEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransitionResult result = transition(TriggerType.USER_ACTION);

        assertThat(result.performed()).isTrue();
        assertThat(result.toState()).isEqualTo("APPROVED");
        assertThat(result.emittedEvents()).containsExactly("EVT1", "EVT2");
        assertThat(writtenStatus.get()).isEqualTo("APPROVED");

        InOrder inOrder = Mockito.inOrder(action1, action2);
        inOrder.verify(action1).execute(any());
        inOrder.verify(action2).execute(any());

        verify(auditEventRepository).save(argThatAuditEvent(event ->
                event.getEntityType().equals(ENTITY_TYPE.name())
                        && event.getEntityId().equals(ENTITY_ID)
                        && event.getAction().equals("T_APPROVE")));
    }

    @Test
    void throwsWhenNoTransitionConfiguredForFromStateAndTrigger() {
        stubModel(List.of());

        assertThrows(NoApplicableTransitionException.class, () -> transition(TriggerType.USER_ACTION));
        verifyNoInteractions(auditEventRepository);
    }

    @Test
    void returnsNotPerformedWhenAllCandidateGuardsFail() {
        TransitionConfig config = transition("T_APPROVE", "APPROVED", TriggerType.USER_ACTION, List.of("BLOCK"));
        stubModel(List.of(config));
        when(guardRegistry.resolve("BLOCK")).thenReturn(alwaysFalse());

        TransitionResult result = transition(TriggerType.USER_ACTION);

        assertThat(result.performed()).isFalse();
        assertThat(result.fromState()).isEqualTo(FROM_STATE);
        assertThat(result.toState()).isNull();
        assertThat(writtenStatus.get()).isNull();
        verifyNoInteractions(auditEventRepository);
        verifyNoInteractions(actionRegistry);
    }

    @Test
    void rejectsUnregisteredToState() {
        TransitionConfig config = transition("T_APPROVE", "GHOST_STATE", TriggerType.USER_ACTION, List.of());
        stubModel(List.of(config));
        when(statusRegistryRepository.existsByCodeAndEntityType("GHOST_STATE", ENTITY_TYPE.name())).thenReturn(false);

        assertThrows(InvalidTargetStatusException.class, () -> transition(TriggerType.USER_ACTION));

        assertThat(writtenStatus.get()).isNull();
        verifyNoInteractions(actionRegistry);
        verifyNoInteractions(auditEventRepository);
    }

    private TransitionResult transition(TriggerType trigger) {
        TransitionContext context = new TransitionContext(null, UUID.randomUUID(), ActorType.USER, Map.of());
        return engine.transition(ENTITY_TYPE, ENTITY_ID, CONFIG_ID, FROM_STATE, trigger, context, writtenStatus::set);
    }

    private void stubModel(List<TransitionConfig> candidates) {
        TriggerType trigger = candidates.isEmpty() ? TriggerType.USER_ACTION : candidates.get(0).getTrigger();
        Map<TransitionKey, List<TransitionConfig>> byKey =
                candidates.isEmpty() ? Map.of() : Map.of(new TransitionKey(FROM_STATE, trigger), candidates);
        when(modelFactory.loadModel(CONFIG_ID)).thenReturn(
                new TransitionModel(ENTITY_TYPE, CONFIG_ID, Map.of(), byKey));
    }

    private static Guard alwaysTrue() {
        return context -> true;
    }

    private static Guard alwaysFalse() {
        return context -> false;
    }

    private static TransitionConfig transition(String code, String toState, TriggerType trigger, List<String> guards) {
        return transition(code, toState, trigger, guards, List.of(), List.of());
    }

    private static TransitionConfig transition(
            String code, String toState, TriggerType trigger, List<String> guards, List<String> actions, List<String> emits) {
        return TransitionConfig.builder()
                .id(UUID.randomUUID())
                .code(code)
                .fromState(FROM_STATE)
                .toState(toState)
                .trigger(trigger)
                .guards(guards)
                .actions(actions)
                .emits(emits)
                .priority(0)
                .active(true)
                .createdAt(Instant.now())
                .build();
    }

    private static AuditEvent argThatAuditEvent(java.util.function.Predicate<AuditEvent> predicate) {
        return Mockito.argThat(predicate::test);
    }
}
