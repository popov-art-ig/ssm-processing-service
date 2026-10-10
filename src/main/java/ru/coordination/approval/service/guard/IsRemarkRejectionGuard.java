package ru.coordination.approval.service.guard;

import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.Remark;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * Различает финальные исходы замечания: вызов является отклонением. Резолвится
 * {@code guard_registry.handler = 'isRemarkRejectionGuard'} (миграция V24, код реестра
 * {@code IsRemarkRejection}); включён в guards перехода {@code RejectRemark}
 * ({@code InProgress → Rejected}, {@code USER_ACTION}).
 *
 * <p>Из состояния {@code InProgress} доступны два перехода — {@code ProcessRemark} (→ Processed)
 * и {@code RejectRemark} (→ Rejected), причём оба предиката прав истинны для одного и того же
 * пользователя: {@code AssignRemarkToAuthorAction} назначает ответственным автора замечания,
 * поэтому и {@code IsRemarkAssignee}, и {@code IsRemarkAuthor} проходят для автора. ModelFactory
 * сортирует кандидатов по {@code priority}, затем по {@code code}, и «ProcessRemark»
 * лексикографически меньше «RejectRemark» — то есть вызов {@code rejectRemark} уходил бы в
 * {@code ProcessRemark} и замечание получало бы статус {@code Processed}.
 *
 * <p>Поэтому исход различается по намерению вызывающего кода: {@code RemarkService} кладёт в
 * параметры перехода {@code reason} для отклонения и {@code resolution} для обработки, а этот
 * guard проверяет наличие соответствующего параметра. Так выбор перехода перестаёт зависеть от
 * лексикографического порядка кодов.
 *
 * <p><b>Кода в спецификации нет</b> — как и у {@link IsRemarkResolutionGuard}: в
 * {@code 05_guards_actions_registry.md} §4.6 исходы замечания разделены состояниями
 * ({@code MarkResolved} и {@code MarkRejected}, §5.7), а не предикатами прав. Проверка пары
 * {@code reason}/{@code resolution} обеспечивает взаимоисключаемость: при корректном вызове
 * ровно один из двух guard'ов истинен, поэтому недетерминизм возникает только при передаче
 * обоих параметров сразу. Побочных эффектов нет.
 */
@Component("isRemarkRejectionGuard")
public class IsRemarkRejectionGuard implements Guard {

    /** @return {@code true}, если в параметрах перехода передан {@code reason} (отклонение) */
    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof Remark)) {
            return false;
        }
        return context.parameters().get("reason") != null;
    }
}
