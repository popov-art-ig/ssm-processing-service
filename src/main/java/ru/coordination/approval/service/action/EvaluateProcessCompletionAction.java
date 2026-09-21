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
 * A-S-005 «Оценить завершение процесса» (PHASE-07, не описан явно в реестре, аналог
 * {@link EvaluateAggregationAction}). Резолвится
 * {@code action_registry.handler = 'evaluateProcessCompletionAction'} (миграция V22).
 *
 * <p>Проверяет, все ли этапы процесса завершены, и если да — программно запускает один из переходов
 * {@code PROCESS}: {@code ProcessApprovedWithComments} (если хотя бы один этап
 * {@code ApprovedWithComments}) или {@code ProcessApproved} (если все этапы {@code Approved}).
 *
 * <p>Вызывается как action переходов {@code StageApproved} и {@code StageApprovedWithComments}
 * (уровень {@code STAGE}) после {@code ActivateNextStage}. НЕ вызывается при переходе
 * {@code StageOnRework} — возврат процесса на доработку реализуется в PHASE-08.
 *
 * <p>Вложенный вызов {@link TransitionEngine#transition} выполняется в той же транзакции, что и
 * внешний переход этапа. Если ни один переход процесса не выполнен
 * ({@link TransitionResult#performed()} {@code == false}, guards не прошли) — это не ошибка:
 * action вызывается после каждого закрытия этапа, но срабатывает только когда все этапы завершены
 * (guard {@code AllStagesCompleted}).
 */
@Component("evaluateProcessCompletionAction")
@RequiredArgsConstructor
public class EvaluateProcessCompletionAction implements Action {

    private final StateMachineConfigRepository stateMachineConfigRepository;
    private final TransitionEngine transitionEngine;

    @Override
    public void execute(TransitionContext context) {
        StageInstance stage = (StageInstance) context.entity();
        ProcessInstance process = stage.getProcess();

        // Резолвить PROCESS config
        StateMachineConfig processConfig = stateMachineConfigRepository
                .findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.PROCESS,
                        process.getProcessType(),
                        process.getConfigVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "No StateMachineConfig for entityType=PROCESS, processType=%s, version=%d"
                                .formatted(process.getProcessType(), process.getConfigVersion())));

        // Создать контекст для перехода процесса (SYSTEM trigger)
        TransitionContext processContext =
                new TransitionContext(process, context.actorId(), ActorType.SYSTEM, Map.of());

        // Попытаться выполнить переход — TransitionEngine сам проверит guards и выберет
        // между ProcessApprovedWithComments (если есть комментарии) и ProcessApproved
        TransitionResult result = transitionEngine.transition(
                EntityType.PROCESS,
                process.getId(),
                processConfig.getId(),
                process.getStatus(),
                TriggerType.SYSTEM_ACTION,
                processContext,
                process::setStatus);

        // Если не выполнен (не все этапы завершены) — молча выйти
        if (!result.performed()) {
            // no-op: ещё не все этапы завершены или guards не прошли
        }
    }
}
