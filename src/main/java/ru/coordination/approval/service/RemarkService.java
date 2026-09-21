package ru.coordination.approval.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.ProcessRepository;
import ru.coordination.approval.domain.process.Remark;
import ru.coordination.approval.domain.process.RemarkRepository;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.domain.statemachine.TriggerType;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.TransitionEngine;
import ru.coordination.approval.engine.TransitionResult;
import ru.coordination.approval.engine.model.StateMachineConfigRepository;

/**
 * Сервис управления замечаниями (PHASE-11).
 * Предоставляет методы для создания, обработки и отклонения замечаний.
 */
@Service
@RequiredArgsConstructor
public class RemarkService {

    private final RemarkRepository remarkRepository;
    private final ProcessRepository processRepository;
    private final StateMachineConfigRepository stateMachineConfigRepository;
    private final TransitionEngine transitionEngine;

    @Transactional
    public Remark createRemark(
            UUID processId,
            UUID stageId,
            String text,
            UUID actorId) {

        ProcessInstance process = processRepository.findById(processId)
                .orElseThrow(() -> new IllegalArgumentException("Process not found"));

        StageInstance stage = process.getStages().stream()
                .filter(s -> s.getId().equals(stageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Stage not found"));

        StateMachineConfig config = stateMachineConfigRepository
                .findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.REMARK,
                        process.getProcessType(),
                        process.getConfigVersion())
                .orElseThrow(() -> new IllegalStateException("Remark state machine config not found"));

        Remark remark = Remark.builder()
                .id(UUID.randomUUID())
                .process(process)
                .stage(stage)
                .authorId(actorId)
                .text(text)
                .status("Draft")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        remarkRepository.save(remark);

        Map<String, Object> parameters = Map.of();
        TransitionContext context = new TransitionContext(
                remark, actorId, ActorType.USER, parameters);

        TransitionResult result = transitionEngine.transition(
                EntityType.REMARK,
                remark.getId(),
                config.getId(),
                remark.getStatus(),
                TriggerType.USER_ACTION,
                context,
                remark::setStatus);

        if (!result.performed()) {
            throw new IllegalStateException("Failed to create remark: transition not performed");
        }

        return remarkRepository.save(remark);
    }

    @Transactional
    public TransitionResult processRemark(UUID remarkId, String resolution, UUID actorId) {
        Remark remark = remarkRepository.findById(remarkId)
                .orElseThrow(() -> new IllegalArgumentException("Remark not found"));

        StateMachineConfig config = stateMachineConfigRepository
                .findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.REMARK,
                        remark.getProcess().getProcessType(),
                        remark.getProcess().getConfigVersion())
                .orElseThrow(() -> new IllegalStateException("Remark config not found"));

        Map<String, Object> parameters = Map.of("resolution", resolution);
        TransitionContext context = new TransitionContext(
                remark, actorId, ActorType.USER, parameters);

        return transitionEngine.transition(
                EntityType.REMARK,
                remark.getId(),
                config.getId(),
                remark.getStatus(),
                TriggerType.USER_ACTION,
                context,
                remark::setStatus);
    }

    @Transactional
    public TransitionResult rejectRemark(UUID remarkId, String reason, UUID actorId) {
        Remark remark = remarkRepository.findById(remarkId)
                .orElseThrow(() -> new IllegalArgumentException("Remark not found"));

        StateMachineConfig config = stateMachineConfigRepository
                .findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.REMARK,
                        remark.getProcess().getProcessType(),
                        remark.getProcess().getConfigVersion())
                .orElseThrow(() -> new IllegalStateException("Remark config not found"));

        Map<String, Object> parameters = Map.of("reason", reason);
        TransitionContext context = new TransitionContext(
                remark, actorId, ActorType.USER, parameters);

        return transitionEngine.transition(
                EntityType.REMARK,
                remark.getId(),
                config.getId(),
                remark.getStatus(),
                TriggerType.USER_ACTION,
                context,
                remark::setStatus);
    }
}
