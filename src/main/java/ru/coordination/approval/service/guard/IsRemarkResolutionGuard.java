package ru.coordination.approval.service.guard;

import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.Remark;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * Различает финальные исходы замечания: вызов является обработкой (исправлением).
 *
 * <p>Парный guard к {@link IsRemarkRejectionGuard} — см. его javadoc: без такого различения
 * исход выбирался бы по лексикографическому порядку кодов переходов, и {@code rejectRemark}
 * приводил бы к статусу {@code Processed}.
 */
@Component("isRemarkResolutionGuard")
public class IsRemarkResolutionGuard implements Guard {

    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof Remark)) {
            return false;
        }
        return context.parameters().get("resolution") != null;
    }
}
