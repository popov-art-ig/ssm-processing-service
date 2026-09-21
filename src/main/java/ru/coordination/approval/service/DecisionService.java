package ru.coordination.approval.service;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.ProcessRepository;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.domain.statemachine.TriggerType;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.TransitionEngine;
import ru.coordination.approval.engine.TransitionResult;
import ru.coordination.approval.engine.model.StateMachineConfigRepository;

/**
 * Use-case сервис для принятия решения участником (PHASE-05).
 */
@Service
@RequiredArgsConstructor
public class DecisionService {

    private final ProcessRepository processRepository;
    private final StateMachineConfigRepository stateMachineConfigRepository;
    private final TransitionEngine transitionEngine;

    @Transactional
    public TransitionResult decide(
            UUID processId,
            UUID stageId,
            UUID participantId,
            String decisionType,
            String comment,
            UUID actorId) {

        // Валидация decisionType и comment
        validateDecision(decisionType, comment);

        // 1. Загрузить ProcessInstance
        ProcessInstance process = processRepository.findById(processId)
                .orElseThrow(() -> new IllegalArgumentException("Process not found: " + processId));

        // 2. Найти StageInstance
        StageInstance stage = process.getStages().stream()
                .filter(s -> s.getId().equals(stageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Stage not found: " + stageId));

        // 3. Найти Participant в текущей итерации
        StageIteration currentIteration = stage.getIterations().stream()
                .max(Comparator.comparing(StageIteration::getIterationIdx))
                .orElseThrow(() -> new IllegalStateException("No iterations found for stage: " + stageId));

        Participant participant = currentIteration.getParticipants().stream()
                .filter(p -> p.getId().equals(participantId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Participant not found: " + participantId));

        // 4. Резолвить configId для PARTICIPANT state machine
        StateMachineConfig participantConfig = stateMachineConfigRepository
                .findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.PARTICIPANT,
                        process.getProcessType(),
                        process.getConfigVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "Participant state machine config not found for processType=%s, version=%d"
                                .formatted(process.getProcessType(), process.getConfigVersion())));

        // 5. Собрать TransitionContext с decisionType и comment в parameters
        Map<String, Object> parameters = Map.of(
                "decisionType", decisionType,
                "comment", comment != null ? comment : "");

        TransitionContext context = new TransitionContext(
                participant,
                actorId,
                ActorType.USER,
                parameters);

        // 6. Вызвать TransitionEngine
        return transitionEngine.transition(
                EntityType.PARTICIPANT,
                participant.getId(),
                participantConfig.getId(),
                participant.getStatus(),
                TriggerType.USER_ACTION,
                context,
                participant::setStatus);
    }

    private void validateDecision(String decisionType, String comment) {
        if ("APPROVE_WITH_COMMENTS".equals(decisionType) && (comment == null || comment.isBlank())) {
            throw new IllegalArgumentException("Comment is required for APPROVE_WITH_COMMENTS");
        }
        if ("REJECT".equals(decisionType) && (comment == null || comment.isBlank())) {
            throw new IllegalArgumentException("Comment is required for REJECT");
        }
    }
}
