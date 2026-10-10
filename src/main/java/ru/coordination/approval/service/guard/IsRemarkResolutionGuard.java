package ru.coordination.approval.service.guard;

import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.Remark;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * Различает финальные исходы замечания: вызов является обработкой (исправлением). Резолвится
 * {@code guard_registry.handler = 'isRemarkResolutionGuard'} (миграция V24, код реестра
 * {@code IsRemarkResolution}); включён в guards перехода {@code ProcessRemark}
 * ({@code InProgress → Processed}, {@code USER_ACTION}).
 *
 * <p>Парный guard к {@link IsRemarkRejectionGuard} — см. его javadoc: без такого различения
 * исход выбирался бы по лексикографическому порядку кодов переходов, и {@code rejectRemark}
 * приводил бы к статусу {@code Processed}.
 *
 * <p>Проверяет наличие параметра {@code resolution}: {@code true} — вызывающий код обрабатывает
 * замечание; {@code false} — параметра нет (или сущность перехода не {@link Remark}). Побочных
 * эффектов нет.
 */
@Component("isRemarkResolutionGuard")
public class IsRemarkResolutionGuard implements Guard {

    /**
     * @return {@code true}, если в параметрах перехода передан {@code resolution} (обработка
     *         замечания)
     */
    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof Remark)) {
            return false;
        }
        return context.parameters().get("resolution") != null;
    }
}
