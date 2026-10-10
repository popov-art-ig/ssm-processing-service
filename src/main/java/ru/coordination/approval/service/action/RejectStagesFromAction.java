package ru.coordination.approval.service.action;

import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

/**
 * A-RT-002 «Пометить активные этапы отклонёнными» (PHASE-08, источник:
 * {@code 05_guards_actions_registry.md} §5.3). Резолвится {@code action_registry.handler =
 * 'rejectStagesFromAction'} (миграция V23, код реестра {@code RejectStagesFrom}); включён в
 * actions перехода {@code ResumeProcess} ({@code OnRework → InProgress}, {@code USER_ACTION}),
 * всегда перед {@link ActivateTargetStageAction}.
 *
 * <p><b>Какое состояние меняет.</b> Статусы этапов, которые «пропускаются» при возврате: всем
 * этапам с {@code targetStageOrderIdx < orderIdx <= onReworkStage.orderIdx} присваивается
 * {@code Rejected}. Это единственное место в модуле, где этап получает терминальный
 * {@code Rejected}: агрегация решений ведёт только в {@code OnRework}
 * ({@link ru.coordination.approval.service.guard.AggregationReworkGuard}).
 *
 * <p><b>Отклонения от §5.3.</b>
 * <ul>
 *   <li>спецификация задаёт условие {@code stage.orderIdx > targetStage.orderIdx} без верхней
 *       границы — то есть «все этапы после целевого». Здесь добавлено
 *       {@code orderIdx <= onReworkStage.orderIdx}: этапы, расположенные <i>после</i> ушедшего на
 *       доработку, при возобновлении ещё не пройдены и остаются {@code Pending}; отклонять их
 *       значило бы закрыть маршрут вперёд, который пользователь ещё не проходил;</li>
 *   <li>спецификация ограничивает действие статусом {@code Active} («если {@code stage.status ==
 *       Active}»), здесь статус не проверяется — перезаписываются любые этапы из диапазона.
 *       Для возврата с доработки это безопасно (этапы диапазона уже закрыты), но поведение
 *       шире описанного;</li>
 *   <li>статус пишется напрямую в сущность, без перехода state machine, хотя ADR-002/ADR-028
 *       требуют менять состояние только через явный переход. Исключение здесь то же, что
 *       спецификация допускает для {@code A-RT-001 SupersedeStagesFrom}/{@code A-RT-002}:
 *       состояние «отклонён при возврате» не имеет собственного перехода в карте этапа.</li>
 * </ul>
 *
 * <p>Этап, ушедший на доработку, ищется в in-memory коллекции {@code process.getStages()} по
 * статусу {@code OnRework} — действие выполняется в контексте процесса, где все этапы уже
 * загружены, и на этом шаге дополнительный запрос к БД не нужен.
 */
@Component("rejectStagesFromAction")
public class RejectStagesFromAction implements Action {

    /**
     * Помечает этапы между целевым и ушедшим на доработку статусом {@code Rejected}.
     *
     * @param context контекст перехода {@code ResumeProcess}; обязательный параметр —
     *                {@code targetStageOrderIdx} (индекс целевого этапа)
     * @throws IllegalStateException если в процессе нет ни одного этапа со статусом
     *         {@code OnRework} — вызывающий код возобновляет процесс, который на доработку не
     *         уходил
     */
    @Override
    public void execute(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();
        Integer targetStageOrderIdx = (Integer) context.parameters().get("targetStageOrderIdx");

        StageInstance onReworkStage = process.getStages().stream()
                .filter(s -> "OnRework".equals(s.getStatus()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No OnRework stage found"));

        process.getStages().stream()
                .filter(s -> s.getOrderIdx() > targetStageOrderIdx
                        && s.getOrderIdx() <= onReworkStage.getOrderIdx())
                .forEach(stage -> stage.setStatus("Rejected"));
    }
}
