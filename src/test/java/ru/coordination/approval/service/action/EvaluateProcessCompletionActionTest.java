package ru.coordination.approval.service.action;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.domain.statemachine.TriggerType;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.TransitionEngine;
import ru.coordination.approval.engine.TransitionResult;
import ru.coordination.approval.engine.model.StateMachineConfigRepository;
import static org.assertj.core.api.Assertions.assertThat;

class EvaluateProcessCompletionActionTest {

    private StateMachineConfigRepository stateMachineConfigRepository;
    private TransitionEngine transitionEngine;
    private EvaluateProcessCompletionAction action;

    @BeforeEach
    void setUp() {
        stateMachineConfigRepository = mock(StateMachineConfigRepository.class);
        transitionEngine = mock(TransitionEngine.class);
        action = new EvaluateProcessCompletionAction(stateMachineConfigRepository, transitionEngine);
    }

    @Test
    void triggersProcessTransitionWhenAllStagesCompleted() {
        ProcessInstance process = createProcessWithStages("Approved", "Approved", "Approved");
        StageInstance stage = process.getStages().get(0);

        StateMachineConfig processConfig = createProcessConfig();
        when(stateMachineConfigRepository.findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.PROCESS, ProcessType.STANDARD, 1))
                .thenReturn(Optional.of(processConfig));

        when(transitionEngine.transition(
                        eq(EntityType.PROCESS),
                        eq(process.getId()),
                        eq(processConfig.getId()),
                        eq("InProgress"),
                        eq(TriggerType.SYSTEM_ACTION),
                        any(TransitionContext.class),
                        any(Consumer.class)))
                .thenReturn(TransitionResult.performed("InProgress", "Approved", "ProcessApproved", new String[] {}));

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        action.execute(context);

        ArgumentCaptor<TransitionContext> contextCaptor = ArgumentCaptor.forClass(TransitionContext.class);
        verify(transitionEngine)
                .transition(
                        eq(EntityType.PROCESS),
                        eq(process.getId()),
                        eq(processConfig.getId()),
                        eq("InProgress"),
                        eq(TriggerType.SYSTEM_ACTION),
                        contextCaptor.capture(),
                        any(Consumer.class));

        TransitionContext capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.entity()).isEqualTo(process);
        assertThat(capturedContext.actorType()).isEqualTo(ActorType.SYSTEM);
    }

    @Test
    void doesNothingWhenNotAllStagesCompleted() {
        ProcessInstance process = createProcessWithStages("Approved", "Active", "Pending");
        StageInstance stage = process.getStages().get(0);

        StateMachineConfig processConfig = createProcessConfig();
        when(stateMachineConfigRepository.findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.PROCESS, ProcessType.STANDARD, 1))
                .thenReturn(Optional.of(processConfig));

        when(transitionEngine.transition(
                        eq(EntityType.PROCESS),
                        eq(process.getId()),
                        eq(processConfig.getId()),
                        eq("InProgress"),
                        eq(TriggerType.SYSTEM_ACTION),
                        any(TransitionContext.class),
                        any(Consumer.class)))
                .thenReturn(TransitionResult.notPerformed("InProgress"));

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        action.execute(context);

        verify(transitionEngine)
                .transition(
                        eq(EntityType.PROCESS),
                        eq(process.getId()),
                        eq(processConfig.getId()),
                        eq("InProgress"),
                        eq(TriggerType.SYSTEM_ACTION),
                        any(TransitionContext.class),
                        any(Consumer.class));
    }

    @Test
    void throwsExceptionWhenProcessConfigNotFound() {
        ProcessInstance process = createProcessWithStages("Approved", "Approved", "Approved");
        StageInstance stage = process.getStages().get(0);

        when(stateMachineConfigRepository.findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.PROCESS, ProcessType.STANDARD, 1))
                .thenReturn(Optional.empty());

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThatThrownBy(() -> action.execute(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No StateMachineConfig");
    }

    @Test
    void handlesProcessWithMixedApprovedStatuses() {
        ProcessInstance process = createProcessWithStages("Approved", "ApprovedWithComments", "Approved");
        StageInstance stage = process.getStages().get(2);

        StateMachineConfig processConfig = createProcessConfig();
        when(stateMachineConfigRepository.findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.PROCESS, ProcessType.STANDARD, 1))
                .thenReturn(Optional.of(processConfig));

        when(transitionEngine.transition(
                        eq(EntityType.PROCESS),
                        eq(process.getId()),
                        eq(processConfig.getId()),
                        eq("InProgress"),
                        eq(TriggerType.SYSTEM_ACTION),
                        any(TransitionContext.class),
                        any(Consumer.class)))
                .thenReturn(TransitionResult.performed(
                        "InProgress", "ApprovedWithComments", "ProcessApprovedWithComments", new String[] {}));

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        action.execute(context);

        verify(transitionEngine)
                .transition(
                        eq(EntityType.PROCESS),
                        eq(process.getId()),
                        eq(processConfig.getId()),
                        eq("InProgress"),
                        eq(TriggerType.SYSTEM_ACTION),
                        any(TransitionContext.class),
                        any(Consumer.class));
    }

    private ProcessInstance createProcessWithStages(String... statuses) {
        ProcessInstance process = ProcessInstance.builder()
                .id(UUID.randomUUID())
                .processType(ProcessType.STANDARD)
                .configVersion(1)
                .status("InProgress")
                .initiatorId(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();

        int orderIdx = 1;
        for (String status : statuses) {
            StageInstance stage = StageInstance.builder()
                    .id(UUID.randomUUID())
                    .process(process)
                    .orderIdx(orderIdx++)
                    .status(status)
                    .createdAt(Instant.now())
                    .build();
            process.getStages().add(stage);
        }

        return process;
    }

    private StateMachineConfig createProcessConfig() {
        return StateMachineConfig.builder()
                .id(UUID.randomUUID())
                .entityType(EntityType.PROCESS)
                .processType(ProcessType.STANDARD)
                .version(1)
                .build();
    }
}
