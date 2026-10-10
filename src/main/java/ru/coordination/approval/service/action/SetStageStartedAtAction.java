package ru.coordination.approval.service.action;

import java.time.Instant;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

/**
 * A-S-002 «Установить время старта этапа» — фиксирует {@code stage.startedAt} (PHASE-04,
 * источник: {@code 05_guards_actions_registry.md} §5.2). Резолвится
 * {@code action_registry.handler = 'setStageStartedAtAction'} (миграция V19); включён в actions
 * переходов {@code ActivateStage} (V19) и {@code ReactivateStage} (V23).
 *
 * <p><b>Побочный эффект</b> — {@code startedAt = Instant.now()} на {@link StageInstance}; запись
 * в БД выполняет транзакция {@code TransitionEngine}. Должен выполняться до
 * {@link CalcStageDueAtAction} в списке {@code actions} перехода — тот читает
 * {@code stage.getStartedAt()} (порядок задан миграцией V19, не этим классом).
 *
 * <p>При повторной активации этапа после возврата на доработку {@code startedAt} перезаписывается
 * текущим моментом: срок на повторное прохождение отсчитывается заново, и «просроченность»
 * предыдущей попытки не переносится на новую итерацию.
 */
@Component("setStageStartedAtAction")
public class SetStageStartedAtAction implements Action {

    /** Записывает текущий момент в {@code stage.startedAt}. */
    @Override
    public void execute(TransitionContext context) {
        StageInstance stage = (StageInstance) context.entity();
        stage.setStartedAt(Instant.now());
    }
}
