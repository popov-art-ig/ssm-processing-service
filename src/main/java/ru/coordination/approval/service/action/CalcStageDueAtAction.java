package ru.coordination.approval.service.action;

import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

/**
 * A-S-003 «Вычисляет stage.dueAt = stage.startedAt + stage.duration» (PHASE-04, источник:
 * {@code 05_guards_actions_registry.md} §5.2). Резолвится {@code action_registry.handler =
 * 'calcStageDueAtAction'} (миграция V19).
 *
 * <p><b>Единица измерения {@code duration}</b> не указана явно ни в одном документе,
 * доступном для этого тикета (открытый вопрос №2). Принята как дни — по аналогии с
 * единственным похожим интервалом в модуле (автоархивация, {@code A-P-007
 * ScheduleAutoArchive}, параметр в днях). Если предположение неверно, единственное место
 * правки — эта строка (и одноимённый тест {@code CalcStageDueAtActionTest}).
 *
 * <p>Должен выполняться после {@link SetStageStartedAtAction} в списке {@code actions}
 * перехода (порядок задан миграцией V19) — иначе {@code stage.getStartedAt()} ещё
 * {@code null}.
 */
@Component("calcStageDueAtAction")
public class CalcStageDueAtAction implements Action {

    @Override
    public void execute(TransitionContext context) {
        StageInstance stage = (StageInstance) context.entity();
        stage.setDueAt(stage.getStartedAt().plus(stage.getDuration(), ChronoUnit.DAYS));
    }
}
