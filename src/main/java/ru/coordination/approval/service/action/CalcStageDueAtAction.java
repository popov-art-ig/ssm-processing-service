package ru.coordination.approval.service.action;

import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

/**
 * A-S-003 «Рассчитать срок этапа» — вычисляет
 * {@code stage.dueAt = stage.startedAt + stage.duration} (PHASE-04, источник:
 * {@code 05_guards_actions_registry.md} §5.2). Резолвится {@code action_registry.handler =
 * 'calcStageDueAtAction'} (миграция V19); включён в actions переходов {@code ActivateStage} (V19)
 * и {@code ReactivateStage} (V23), всегда последним в списке — потому что зависит от
 * {@code stage.startedAt}, который записывает {@link SetStageStartedAtAction}.
 *
 * <p><b>Побочный эффект</b> — {@code dueAt} на {@link StageInstance}; в БД фиксируется транзакцией
 * {@code TransitionEngine}. Действие не проверяет, что {@code startedAt} не {@code null}: при
 * нарушении порядка {@code actions} в карте перехода оно бросит {@link NullPointerException}, а не
 * молча посчитает срок от текущего момента.
 *
 * <p><b>Единица измерения {@code duration}</b> не указана явно ни в одном документе,
 * доступном для этого тикета (открытый вопрос №2). Принята как дни — по аналогии с
 * единственным похожим интервалом в модуле (автоархивация, {@code A-P-007
 * ScheduleAutoArchive}, параметр в днях). Если предположение неверно, единственное место
 * правки — эта строка (и одноимённый тест {@code CalcStageDueAtActionTest}).
 */
@Component("calcStageDueAtAction")
public class CalcStageDueAtAction implements Action {

    /**
     * Записывает {@code dueAt = startedAt + duration} (в днях).
     *
     * @throws NullPointerException если {@code stage.startedAt} ещё не заполнен — порядок
     *         {@code actions} в карте перехода нарушен
     */
    @Override
    public void execute(TransitionContext context) {
        StageInstance stage = (StageInstance) context.entity();
        stage.setDueAt(stage.getStartedAt().plus(stage.getDuration(), ChronoUnit.DAYS));
    }
}
