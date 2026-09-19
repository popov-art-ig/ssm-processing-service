package ru.coordination.approval.service;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.ProcessRepository;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.domain.statemachine.TriggerType;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.TransitionEngine;
import ru.coordination.approval.engine.TransitionResult;
import ru.coordination.approval.engine.model.StateMachineConfigRepository;

/**
 * Первый доменный сервис, вызывающий {@code TransitionEngine} (PHASE-03) — только переход
 * {@code StartProcess} (STANDARD, {@code Draft -> InProgress}). Не создаёт процесс «с нуля»
 * (нет {@code MatchService}/{@code RouteGeneratorService}, см. тикет) — работает с уже
 * существующим {@link ProcessInstance}.
 */
@Service
@RequiredArgsConstructor
public class ProcessService {

    private final ProcessRepository processRepository;
    private final StateMachineConfigRepository stateMachineConfigRepository;
    private final TransitionEngine transitionEngine;

    @Transactional
    public TransitionResult startProcess(UUID processId, UUID actorId) {
        ProcessInstance process = processRepository.findById(processId)
                .orElseThrow(() -> new IllegalArgumentException("ProcessInstance not found: " + processId));

        StateMachineConfig config = stateMachineConfigRepository
                .findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.PROCESS, process.getProcessType(), process.getConfigVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "No StateMachineConfig for entityType=PROCESS, processType=%s, version=%d"
                                .formatted(process.getProcessType(), process.getConfigVersion())));

        TransitionContext context = new TransitionContext(process, actorId, ActorType.USER, Map.of());

        return transitionEngine.transition(
                EntityType.PROCESS,
                process.getId(),
                config.getId(),
                process.getStatus(),
                TriggerType.USER_ACTION,
                context,
                process::setStatus);
    }
}
