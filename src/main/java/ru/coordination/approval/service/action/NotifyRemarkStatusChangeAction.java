package ru.coordination.approval.service.action;

import org.springframework.stereotype.Component;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

/**
 * A-R-002 «Публикация события об изменении статуса замечания» (PHASE-11).
 * PHASE-11: заглушка — no-op.
 * TODO PHASE-19: реализовать публикацию событий через EventPublisher.
 */
@Component("notifyRemarkStatusChangeAction")
public class NotifyRemarkStatusChangeAction implements Action {

    @Override
    public void execute(TransitionContext context) {
        // no-op: заглушка для PHASE-11
    }
}
