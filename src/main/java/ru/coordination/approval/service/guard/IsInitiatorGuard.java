package ru.coordination.approval.service.guard;

import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-P-001 «Текущий пользователь — инициатор процесса» (PHASE-03, источник:
 * {@code 05_guards_actions_registry.md} §4.1). Резолвится {@code guard_registry.handler =
 * 'isInitiatorGuard'} (миграция V18).
 */
@Component("isInitiatorGuard")
public class IsInitiatorGuard implements Guard {

    @Override
    public boolean evaluate(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();
        return context.actorId() != null && context.actorId().equals(process.getInitiatorId());
    }
}
