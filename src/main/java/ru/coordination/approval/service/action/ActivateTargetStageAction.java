package ru.coordination.approval.service.action;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.domain.statemachine.TriggerType;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.TransitionEngine;
import ru.coordination.approval.engine.TransitionResult;
import ru.coordination.approval.engine.model.StateMachineConfigRepository;
import ru.coordination.approval.engine.registry.Action;

/**
 * A-RT-005 «Создаёт новую итерацию целевого этапа, клонирует участников, активирует этап» (PHASE-08).
 * PHASE-08: не клонирует additionalApprovers (список пуст в новой итерации).
 * TODO PHASE-10: клонировать дополнительных согласующих по правилам «кто назначил».
 */
@Component("activateTargetStageAction")
@RequiredArgsConstructor
public class ActivateTargetStageAction implements Action {

    private final StateMachineConfigRepository configRepository;
    private final TransitionEngine transitionEngine;

    @Override
    public void execute(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();
        Integer targetStageOrderIdx = (Integer) context.parameters().get("targetStageOrderIdx");

        StageInstance targetStage = process.getStages().stream()
                .filter(s -> s.getOrderIdx().equals(targetStageOrderIdx))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Target stage not found"));

        Integer maxIterationIdx = targetStage.getIterations().stream()
                .map(StageIteration::getIterationIdx)
                .max(Integer::compareTo)
                .orElse(0);

        StageIteration lastIteration = targetStage.getIterations().stream()
                .filter(it -> it.getIterationIdx().equals(maxIterationIdx))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No iterations found"));

        StageIteration newIteration = StageIteration.builder()
                .id(UUID.randomUUID())
                .stage(targetStage)
                .iterationIdx(maxIterationIdx + 1)
                .status("Active")
                .startedAt(Instant.now())
                .createdAt(Instant.now())
                .participants(new ArrayList<>())
                .build();

        for (Participant oldParticipant : lastIteration.getParticipants()) {
            Participant newParticipant = Participant.builder()
                    .id(UUID.randomUUID())
                    .stageIteration(newIteration)
                    .userId(oldParticipant.getUserId())
                    .organizationId(oldParticipant.getOrganizationId())
                    .role(oldParticipant.getRole())
                    .actorSlotRef(oldParticipant.getActorSlotRef())
                    .resolvedRoleRef(oldParticipant.getResolvedRoleRef())
                    .orderIdx(oldParticipant.getOrderIdx())
                    .status("Pending")
                    .assignedAt(null)
                    .dueAt(null)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            newIteration.getParticipants().add(newParticipant);
        }

        targetStage.getIterations().add(newIteration);

        StateMachineConfig stageConfig = configRepository
                .findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.STAGE,
                        process.getProcessType(),
                        process.getConfigVersion())
                .orElseThrow(() -> new IllegalStateException("Stage config not found"));

        TransitionContext stageContext = new TransitionContext(
                targetStage,
                context.actorId(),
                ActorType.SYSTEM,
                Map.of());

        TransitionResult result = transitionEngine.transition(
                EntityType.STAGE,
                targetStage.getId(),
                stageConfig.getId(),
                targetStage.getStatus(),
                TriggerType.SYSTEM_ACTION,
                stageContext,
                targetStage::setStatus);

        if (!result.performed()) {
            throw new IllegalStateException(
                    "Failed to activate target stage " + targetStage.getId() +
                            " (orderIdx=" + targetStage.getOrderIdx() + ")");
        }
    }
}
