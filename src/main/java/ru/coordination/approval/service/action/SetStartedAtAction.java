package ru.coordination.approval.service.action;

import java.time.Instant;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

/**
 * A-P-002 «Фиксирует process.startedAt» (PHASE-03, источник:
 * {@code 05_guards_actions_registry.md} §5.1). Резолвится {@code action_registry.handler =
 * 'setStartedAtAction'} (миграция V18).
 */
@Component("setStartedAtAction")
public class SetStartedAtAction implements Action {

    @Override
    public void execute(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();
        process.setStartedAt(Instant.now());
    }
}
