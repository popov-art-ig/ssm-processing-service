package ru.coordination.approval.service.action;

import java.util.Comparator;
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
 * A-P-001 «Создаёт задачи участникам активного этапа» (PHASE-04, источник:
 * {@code 05_guards_actions_registry.md} §5.1). Резолвится {@code action_registry.handler =
 * 'assignStageTasksAction'} (миграция V19).
 *
 * <p>В контексте {@code StartProcess} (до этого перехода ни один этап процесса ещё не был
 * активен) означает «активировать первый этап маршрута» — выполняет явный вложенный переход
 * {@code ActivateStage} движка {@code STAGE}-конфига для этапа с минимальным {@code orderIdx},
 * а не пишет {@code stage.status} напрямую (ADR-002/ADR-028 — состояние меняется только через
 * явный переход state machine; тот же принцип, что архитектурно уже подтверждён спекой для
 * {@code A-RT-002 RejectStagesFrom}, инициирующего {@code StageRejectedByReturn} на других
 * сущностях).
 *
 * <p>Вложенный вызов {@link TransitionEngine#transition} выполняется в той же транзакции, что
 * и внешний переход {@code StartProcess} ({@code PROPAGATION.REQUIRED} по умолчанию на обоих
 * {@code @Transactional}-методах) — если {@code ActivateStage} не проходит, откатывается весь
 * {@code StartProcess}.
 *
 * <p><b>Почему действие активирует этап, а не назначает задачи.</b> §5.1 определяет
 * {@code A-P-001} как создание задач участникам активного этапа, но в контексте
 * {@code StartProcess} активного этапа ещё не существует — процесс только что вышел из
 * {@code Draft}. Обе половины работы разведены по действиям: «активировать первый этап»
 * выполняет этот класс, а назначение участников активированного этапа — уже
 * {@link AssignParticipantTasksAction} (вложенный переход {@code ActivateStage} указывает его в
 * своих actions, V19). Поэтому реестровая строка {@code AssignStageTasks} в V19 переименована в
 * «Активировать первый этап» и не соответствует буквальному названию из §5.1.
 *
 * <p><b>Почему этап берётся по минимальному {@code orderIdx}, а не по начальному статусу.</b>
 * Первым считается этап с наименьшим {@code orderIdx} — этапы нумеруются с 0, и статус
 * {@code Pending} при старте процесса есть у всех этапов, поэтому по статусу выбрать «первый»
 * невозможно.
 *
 * <p><b>Открытый вопрос №1 тикета — решение.</b> Если вложенный переход не выполнен
 * ({@link TransitionResult#performed()} {@code == false}, guards не прошли), это не
 * игнорируется молча: в объёме этой фазы первый этап всегда проходит
 * {@code PreviousStageCompletedGuard} (guard возвращает {@code true} для этапа без
 * предыдущего) — недостижимый в теории случай, поэтому трактуется как нарушение инварианта и
 * приводит к {@link IllegalStateException}, а не к тихому no-op.
 */
@Component("assignStageTasksAction")
@RequiredArgsConstructor
public class AssignStageTasksAction implements Action {

    private final StateMachineConfigRepository stateMachineConfigRepository;
    private final TransitionEngine transitionEngine;

    /**
     * Активирует первый этап маршрута вложенным переходом {@code ActivateStage}.
     *
     * @param context контекст перехода {@code StartProcess}; сущность — {@link ProcessInstance}
     * @throws IllegalStateException если у процесса нет ни одного этапа, нет {@code STAGE}-конфига
     *         для его типа процесса и версии, либо вложенный переход не выполнен
     */
    @Override
    public void execute(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();

        StageInstance targetStage = process.getStages().stream()
                .min(Comparator.comparing(StageInstance::getOrderIdx))
                .orElseThrow(() -> new IllegalStateException(
                        "Process " + process.getId() + " has no stages, cannot activate the first one"));

        StateMachineConfig stageConfig = stateMachineConfigRepository
                .findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.STAGE, process.getProcessType(), process.getConfigVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "No StateMachineConfig for entityType=STAGE, processType=%s, version=%d"
                                .formatted(process.getProcessType(), process.getConfigVersion())));

        TransitionContext stageContext = new TransitionContext(targetStage, context.actorId(), ActorType.SYSTEM, Map.of());

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
                    "Nested ActivateStage transition did not execute for stage " + targetStage.getId());
        }
    }
}
