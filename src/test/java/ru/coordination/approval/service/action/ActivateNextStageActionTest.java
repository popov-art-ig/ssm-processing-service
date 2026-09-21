package ru.coordination.approval.service.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
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

class ActivateNextStageActionTest {

    private StateMachineConfigRepository stateMachineConfigRepository;
    private TransitionEngine transitionEngine;
    private ActivateNextStageAction action;

    @BeforeEach
    void setUp() {
        stateMachineConfigRepository = mock(StateMachineConfigRepository.class);
        transitionEngine = mock(TransitionEngine.class);
        action = new ActivateNextStageAction(stateMachineConfigRepository, transitionEngine);
    }

    @Test
    void activatesNextStageWhenExists() {
        ProcessInstance process = createProcessWithStages(3);
        StageInstance currentStage = process.getStages().get(0); // orderIdx=1
        StageInstance nextStage = process.getStages().get(1); // orderIdx=2

        StateMachineConfig stageConfig = createStageConfig();
        when(stateMachineConfigRepository.findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.STAGE, ProcessType.STANDARD, 1))
                .thenReturn(Optional.of(stageConfig));

        when(transitionEngine.transition(
                        eq(EntityType.STAGE),
                        eq(nextStage.getId()),
                        eq(stageConfig.getId()),
                        eq("Pending"),
                        eq(TriggerType.SYSTEM_ACTION),
                        any(TransitionContext.class),
                        any(Consumer.class)))
                .thenReturn(TransitionResult.performed("Pending", "Active", "ActivateStage", List.of()));

        TransitionContext context =
                new TransitionContext(currentStage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        action.execute(context);

        ArgumentCaptor<TransitionContext> contextCaptor = ArgumentCaptor.forClass(TransitionContext.class);
        verify(transitionEngine)
                .transition(
                        eq(EntityType.STAGE),
                        eq(nextStage.getId()),
                        eq(stageConfig.getId()),
                        eq("Pending"),
                        eq(TriggerType.SYSTEM_ACTION),
                        contextCaptor.capture(),
                        any(Consumer.class));

        TransitionContext capturedContext = contextCaptor.getValue();
        assertThat(capturedContext.entity()).isEqualTo(nextStage);
        assertThat(capturedContext.actorType()).isEqualTo(ActorType.SYSTEM);
    }

    @Test
    void doesNothingWhenCurrentStageIsLast() {
        ProcessInstance process = createProcessWithStages(2);
        StageInstance lastStage = process.getStages().get(1); // orderIdx=2, no stage with orderIdx=3

        TransitionContext context = new TransitionContext(lastStage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        action.execute(context);

        verify(transitionEngine, never()).transition(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void throwsExceptionWhenActivationFails() {
        ProcessInstance process = createProcessWithStages(2);
        StageInstance currentStage = process.getStages().get(0);
        StageInstance nextStage = process.getStages().get(1);

        StateMachineConfig stageConfig = createStageConfig();
        when(stateMachineConfigRepository.findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.STAGE, ProcessType.STANDARD, 1))
                .thenReturn(Optional.of(stageConfig));

        when(transitionEngine.transition(
                        eq(EntityType.STAGE),
                        eq(nextStage.getId()),
                        eq(stageConfig.getId()),
                        eq("Pending"),
                        eq(TriggerType.SYSTEM_ACTION),
                        any(TransitionContext.class),
                        any(Consumer.class)))
                .thenReturn(TransitionResult.notPerformed("Pending"));

        TransitionContext context =
                new TransitionContext(currentStage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThatThrownBy(() -> action.execute(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to activate next stage");
    }

    @Test
    void throwsExceptionWhenStageConfigNotFound() {
        ProcessInstance process = createProcessWithStages(2);
        StageInstance currentStage = process.getStages().get(0);

        when(stateMachineConfigRepository.findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.STAGE, ProcessType.STANDARD, 1))
                .thenReturn(Optional.empty());

        TransitionContext context =
                new TransitionContext(currentStage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThatThrownBy(() -> action.execute(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No StateMachineConfig");
    }

    private ProcessInstance createProcessWithStages(int stageCount) {
        ProcessInstance process = ProcessInstance.builder()
                .id(UUID.randomUUID())
                .processType(ProcessType.STANDARD)
                .configVersion(1)
                .status("InProgress")
                .initiatorId(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();

        for (int i = 1; i <= stageCount; i++) {
            StageInstance stage = StageInstance.builder()
                    .id(UUID.randomUUID())
                    .process(process)
                    .orderIdx(i)
                    .status("Pending")
                    .createdAt(Instant.now())
                    .build();
            process.getStages().add(stage);
        }

        return process;
    }

    private StateMachineConfig createStageConfig() {
        return StateMachineConfig.builder()
                .id(UUID.randomUUID())
                .entityType(EntityType.STAGE)
                .processType(ProcessType.STANDARD)
                .version(1)
                .build();
    }
}
