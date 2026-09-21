package ru.coordination.approval.service.action;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.domain.statemachine.TriggerType;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.TransitionEngine;
import ru.coordination.approval.engine.TransitionResult;
import ru.coordination.approval.engine.model.StateMachineConfigRepository;
import ru.coordination.approval.engine.registry.Action;

/**
 * Триггер для перехода ProcessRework (PHASE-08). Вызывается после закрытия этапа в OnRework,
 * программно переводит процесс из InProgress в OnRework через TransitionEngine.
 */
@Component("evaluateProcessReworkAction")
@RequiredArgsConstructor
public class EvaluateProcessReworkAction implements Action {

    private final StateMachineConfigRepository configRepository;
    private final TransitionEngine transitionEngine;

    @Override
    public void execute(TransitionContext context) {
        StageInstance stage = (StageInstance) context.entity();
        ProcessInstance process = stage.getProcess();

        StateMachineConfig processConfig = configRepository
                .findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.PROCESS,
                        process.getProcessType(),
                        process.getConfigVersion())
                .orElseThrow(() -> new IllegalStateException("Process config not found"));

        TransitionContext processContext = new TransitionContext(
                process,
                context.actorId(),
                ActorType.SYSTEM,
                Map.of());

        TransitionResult result = transitionEngine.transition(
                EntityType.PROCESS,
                process.getId(),
                processConfig.getId(),
                process.getStatus(),
                TriggerType.SYSTEM_ACTION,
                processContext,
                process::setStatus);

        if (!result.performed()) {
            // no-op: возможно, процесс уже OnRework
        }
    }
}
