package ru.coordination.approval.service.action;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.domain.statemachine.TriggerType;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.TransitionEngine;
import ru.coordination.approval.engine.TransitionResult;
import ru.coordination.approval.engine.model.StateMachineConfigRepository;
import ru.coordination.approval.engine.registry.Action;

/**
 * A-S-004 «Проверяет все решения участников и программно запускает один из трёх переходов этапа»
 * (PHASE-06, источник: {@code 05_guards_actions_registry.md} §5.2). Резолвится
 * {@code action_registry.handler = 'evaluateAggregationAction'} (миграция V21).
 *
 * <p>Вызывается как action перехода {@code Decide} уровня {@code PARTICIPANT} (после
 * {@code RecordDecision} и {@code RecordComment}). Проверяет, все ли участники текущей итерации
 * этапа приняли решение, и если да — программно запускает один из переходов {@code STAGE}:
 * {@code StageApproved}, {@code StageApprovedWithComments} или {@code StageOnRework} в зависимости
 * от режима агрегации ({@code decisionMode}).
 *
 * <p>Вложенный вызов {@link TransitionEngine#transition} выполняется в той же транзакции, что и
 * внешний переход {@code Decide}. Если ни один переход этапа не выполнен
 * ({@link TransitionResult#performed()} {@code == false}, guards не прошли) — это не ошибка:
 * агрегация вызывается после каждого решения участника, но срабатывает только когда все участники
 * приняли решение (guard {@code AllParticipantsDecided}).
 */
@Component("evaluateAggregationAction")
@RequiredArgsConstructor
public class EvaluateAggregationAction implements Action {

    private final StateMachineConfigRepository stateMachineConfigRepository;
    private final TransitionEngine transitionEngine;

    @Override
    public void execute(TransitionContext context) {
        Participant participant = (Participant) context.entity();
        StageInstance stage = participant.getStageIteration().getStage();

        StateMachineConfig stageConfig = stateMachineConfigRepository
                .findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.STAGE,
                        stage.getProcess().getProcessType(),
                        stage.getProcess().getConfigVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "No StateMachineConfig for entityType=STAGE, processType=%s, version=%d"
                                .formatted(
                                        stage.getProcess().getProcessType(),
                                        stage.getProcess().getConfigVersion())));

        TransitionContext stageContext =
                new TransitionContext(stage, context.actorId(), ActorType.SYSTEM, Map.of());

        TransitionResult result = transitionEngine.transition(
                EntityType.STAGE,
                stage.getId(),
                stageConfig.getId(),
                stage.getStatus(),
                TriggerType.SYSTEM_ACTION,
                stageContext,
                stage::setStatus);

        // Если ни один переход не выполнен (AllParticipantsDecided ещё false) — это нормально,
        // агрегация вызывается после каждого решения, но срабатывает только когда все решили
    }
}
