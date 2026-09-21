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
 * A-S-006 «Активировать следующий этап» (PHASE-07, источник:
 * {@code 05_guards_actions_registry.md} §5.2). Резолвится
 * {@code action_registry.handler = 'activateNextStageAction'} (миграция V22).
 *
 * <p>Находит следующий по {@code orderIdx} этап процесса и активирует его (запускает переход
 * {@code ActivateStage}: {@code Pending → Active}). Если следующий этап не найден (текущий этап был
 * последним) — ничего не делает; завершение процесса обрабатывается отдельным action
 * {@code EvaluateProcessCompletion}.
 *
 * <p>Вызывается как action переходов {@code StageApproved} и {@code StageApprovedWithComments}
 * (уровень {@code STAGE}). НЕ вызывается при переходе {@code StageOnRework} — возврат на доработку
 * реализуется в PHASE-08.
 *
 * <p>Вложенный вызов {@link TransitionEngine#transition} выполняется в той же транзакции, что и
 * внешний переход этапа.
 */
@Component("activateNextStageAction")
@RequiredArgsConstructor
public class ActivateNextStageAction implements Action {

    private final StateMachineConfigRepository stateMachineConfigRepository;
    private final TransitionEngine transitionEngine;

    @Override
    public void execute(TransitionContext context) {
        StageInstance currentStage = (StageInstance) context.entity();
        ProcessInstance process = currentStage.getProcess();

        // Найти следующий этап по orderIdx
        StageInstance nextStage = process.getStages().stream()
                .filter(s -> s.getOrderIdx() == currentStage.getOrderIdx() + 1)
                .findFirst()
                .orElse(null);

        if (nextStage == null) {
            // Это был последний этап — завершение процесса произойдёт отдельно (ProcessApproved)
            return;
        }

        // Резолвить STAGE config
        StateMachineConfig stageConfig = stateMachineConfigRepository
                .findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.STAGE,
                        process.getProcessType(),
                        process.getConfigVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "No StateMachineConfig for entityType=STAGE, processType=%s, version=%d"
                                .formatted(process.getProcessType(), process.getConfigVersion())));

        // Создать контекст для перехода этапа (SYSTEM trigger)
        TransitionContext nextStageContext =
                new TransitionContext(nextStage, context.actorId(), ActorType.SYSTEM, Map.of());

        // Выполнить переход ActivateStage (Pending → Active)
        TransitionResult result = transitionEngine.transition(
                EntityType.STAGE,
                nextStage.getId(),
                stageConfig.getId(),
                nextStage.getStatus(),
                TriggerType.SYSTEM_ACTION,
                nextStageContext,
                nextStage::setStatus);

        if (!result.performed()) {
            throw new IllegalStateException(
                    "Failed to activate next stage %s (orderIdx=%d)"
                            .formatted(nextStage.getId(), nextStage.getOrderIdx()));
        }
    }
}
