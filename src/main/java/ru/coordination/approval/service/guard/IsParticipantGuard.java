package ru.coordination.approval.service.guard;

import org.springframework.stereotype.Component;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-R-001 «Текущий пользователь — участник процесса» (PHASE-11).
 * PHASE-11: упрощение — всегда возвращает true (заглушка).
 * TODO: реализовать полную проверку через ProcessRepository.
 */
@Component("isParticipantGuard")
public class IsParticipantGuard implements Guard {

    @Override
    public boolean evaluate(TransitionContext context) {
        return true;
    }
}
