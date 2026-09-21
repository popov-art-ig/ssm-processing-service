package ru.coordination.approval.service.guard;

import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.Remark;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-R-003 «Текущий пользователь — ответственный за обработку замечания» (PHASE-11).
 * Используется в переходах ProcessRemark и RejectRemark для проверки прав assignee.
 */
@Component("isRemarkAssigneeGuard")
public class IsRemarkAssigneeGuard implements Guard {

    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof Remark remark)) {
            return false;
        }

        return context.actorId() != null && context.actorId().equals(remark.getActivatedBy());
    }
}
