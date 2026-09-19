package ru.coordination.approval.service.action;

import java.time.Instant;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

/**
 * A-S-002 «Фиксирует stage.startedAt» (PHASE-04, источник:
 * {@code 05_guards_actions_registry.md} §5.2). Резолвится {@code action_registry.handler =
 * 'setStageStartedAtAction'} (миграция V19). Должен выполняться до {@link CalcStageDueAtAction}
 * в списке {@code actions} перехода — тот читает {@code stage.getStartedAt()} (порядок задан
 * миграцией V19, не этим классом).
 */
@Component("setStageStartedAtAction")
public class SetStageStartedAtAction implements Action {

    @Override
    public void execute(TransitionContext context) {
        StageInstance stage = (StageInstance) context.entity();
        stage.setStartedAt(Instant.now());
    }
}
