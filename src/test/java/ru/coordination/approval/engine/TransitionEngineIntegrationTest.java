package ru.coordination.approval.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import jakarta.persistence.OptimisticLockException;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.audit.AuditEvent;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.common.RegistryScope;
import ru.coordination.approval.domain.registry.ActionRegistryEntry;
import ru.coordination.approval.domain.registry.GuardRegistryEntry;
import ru.coordination.approval.domain.registry.StatusRegistry;
import ru.coordination.approval.domain.statemachine.StateConfig;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.domain.statemachine.TransitionConfig;
import ru.coordination.approval.domain.statemachine.TriggerType;
import ru.coordination.approval.engine.model.StateConfigRepository;
import ru.coordination.approval.engine.model.StateMachineConfigRepository;
import ru.coordination.approval.engine.model.TransitionConfigRepository;
import ru.coordination.approval.engine.registry.ActionRegistryRepository;
import ru.coordination.approval.engine.registry.GuardRegistryRepository;
import ru.coordination.approval.engine.testsupport.EngineTestTarget;
import ru.coordination.approval.engine.testsupport.EngineTestTargetRepository;
import ru.coordination.approval.engine.testsupport.EngineTestTransactionalRunner;
import ru.coordination.approval.engine.testsupport.RecordingAction;

/**
 * Интеграционный тест полного цикла (Testcontainers, PHASE-02 T7/T8): сохранённый
 * {@code StateMachineConfig} → {@code ModelFactory} → {@code TransitionEngine.transition(...)}
 * → сущность в БД имеет новый {@code status}, создан {@code AuditEvent}; плюс оптимистичная
 * блокировка (критерий приёмки 5).
 */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/test-migration")
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransitionEngineIntegrationTest {

    private static final EntityType ENTITY_TYPE = EntityType.PROCESS;
    private static final String FROM_STATE = "IT_DRAFT";
    private static final String TO_STATE = "IT_APPROVED";
    private static final String GUARD_CODE = "IT_GUARD_TRUE";
    private static final String ACTION_CODE = "IT_ACTION_RECORD";
    private static final String TRANSITION_CODE = "IT_APPROVE";

    @Autowired
    private StateMachineConfigRepository stateMachineConfigRepository;
    @Autowired
    private StateConfigRepository stateConfigRepository;
    @Autowired
    private TransitionConfigRepository transitionConfigRepository;
    @Autowired
    private GuardRegistryRepository guardRegistryRepository;
    @Autowired
    private ActionRegistryRepository actionRegistryRepository;
    @Autowired
    private StatusRegistryRepository statusRegistryRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private EngineTestTargetRepository targetRepository;
    @Autowired
    private EngineTestTransactionalRunner runner;
    @Autowired
    private RecordingAction recordingAction;

    private UUID configId;

    @BeforeAll
    void seedReferenceData() {
        Instant now = Instant.now();

        guardRegistryRepository.save(GuardRegistryEntry.builder()
                .code(GUARD_CODE)
                .displayName("Always true (test)")
                .description("Test guard stub, always passes")
                .handler("testAlwaysTrueGuard")
                .scope(RegistryScope.GLOBAL)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());

        actionRegistryRepository.save(ActionRegistryEntry.builder()
                .code(ACTION_CODE)
                .displayName("Recording action (test)")
                .description("Test action stub, records invocations")
                .handler("testRecordingAction")
                .scope(RegistryScope.GLOBAL)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());

        statusRegistryRepository.save(StatusRegistry.builder()
                .code(TO_STATE)
                .entityType(ENTITY_TYPE.name())
                .displayName("Approved (test)")
                .terminal(true)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());

        StateMachineConfig config = StateMachineConfig.builder()
                .id(UUID.randomUUID())
                .version(1)
                .entityType(ENTITY_TYPE)
                .status(LifecycleStatus.PUBLISHED)
                .activeProcessCount(0)
                .createdAt(now)
                .createdBy(UUID.randomUUID())
                .updatedAt(now)
                .build();
        stateMachineConfigRepository.save(config);
        configId = config.getId();

        stateConfigRepository.save(StateConfig.builder()
                .id(UUID.randomUUID())
                .config(config)
                .code(FROM_STATE)
                .displayName("Draft (test)")
                .initial(true)
                .createdAt(now)
                .build());
        stateConfigRepository.save(StateConfig.builder()
                .id(UUID.randomUUID())
                .config(config)
                .code(TO_STATE)
                .displayName("Approved (test)")
                .terminal(true)
                .createdAt(now)
                .build());

        transitionConfigRepository.save(TransitionConfig.builder()
                .id(UUID.randomUUID())
                .config(config)
                .code(TRANSITION_CODE)
                .fromState(FROM_STATE)
                .toState(TO_STATE)
                .trigger(TriggerType.USER_ACTION)
                .guards(List.of(GUARD_CODE))
                .actions(List.of(ACTION_CODE))
                .emits(List.of("IT_EVT_APPROVED"))
                .priority(0)
                .active(true)
                .createdAt(now)
                .build());
    }

    @BeforeEach
    void resetRecordingAction() {
        recordingAction.reset();
    }

    @Test
    void fullCycleUpdatesEntityStatusAndRecordsAudit() {
        UUID targetId = UUID.randomUUID();
        targetRepository.save(new EngineTestTarget(targetId, FROM_STATE));
        UUID actorId = UUID.randomUUID();

        TransitionResult result = runner.transition(
                targetId,
                ENTITY_TYPE,
                configId,
                TriggerType.USER_ACTION,
                new TransitionContext(null, actorId, ActorType.USER, Map.of()));

        assertThat(result.performed()).isTrue();
        assertThat(result.toState()).isEqualTo(TO_STATE);
        assertThat(result.emittedEvents()).containsExactly("IT_EVT_APPROVED");
        assertThat(recordingAction.invocations()).hasSize(1);

        EngineTestTarget persisted = targetRepository.findById(targetId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(TO_STATE);

        List<AuditEvent> auditEvents = auditEventRepository.findAll().stream()
                .filter(e -> e.getEntityId().equals(targetId))
                .toList();
        assertThat(auditEvents).hasSize(1);
        AuditEvent audit = auditEvents.get(0);
        assertThat(audit.getEntityType()).isEqualTo(ENTITY_TYPE.name());
        assertThat(audit.getAction()).isEqualTo(TRANSITION_CODE);
        assertThat(audit.getActorId()).isEqualTo(actorId);
        assertThat(audit.getActorType()).isEqualTo(ActorType.USER);
    }

    @Test
    void concurrentVersionMismatchThrowsOptimisticLockException() {
        UUID targetId = UUID.randomUUID();
        targetRepository.save(new EngineTestTarget(targetId, FROM_STATE));

        assertThrows(OptimisticLockException.class, () -> runner.transitionAfterConcurrentVersionBump(
                targetId,
                ENTITY_TYPE,
                configId,
                TriggerType.USER_ACTION,
                new TransitionContext(null, UUID.randomUUID(), ActorType.USER, Map.of())));

        EngineTestTarget persisted = targetRepository.findById(targetId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(FROM_STATE);
    }
}
