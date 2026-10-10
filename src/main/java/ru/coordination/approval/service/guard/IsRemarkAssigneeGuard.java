package ru.coordination.approval.service.guard;

import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.Remark;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * Guard «Текущий пользователь — ответственный за обработку замечания» (PHASE-11). Резолвится
 * {@code guard_registry.handler = 'isRemarkAssigneeGuard'} (миграция V24, код реестра
 * {@code IsRemarkAssignee}). {@code true} — {@code context.actorId()} совпадает с
 * {@code remark.activatedBy}; {@code false} — иначе. Побочных эффектов нет.
 *
 * <p><b>Ни в один переход не включён.</b> Поиск по {@code transition_config.guards} во всех
 * миграциях не находит {@code IsRemarkAssignee}: переходы {@code StartProcessingRemark},
 * {@code ProcessRemark} и {@code RejectRemark} (V24) защищены другими guard'ами. Заявленная в
 * javadoc-заголовке область применения («переходы {@code ProcessRemark} и {@code RejectRemark}»)
 * не соответствует итоговой карте — guard зарегистрирован, но не подключён.
 *
 * <p><b>Поле {@code activatedBy} как «ответственный».</b> Имя колонки говорит об активации, а не
 * о назначении, поэтому семантика здесь неочевидна: ответственным замечание делает
 * {@link ru.coordination.approval.service.action.AssignRemarkToAuthorAction}, который
 * записывает в {@code activatedBy} автора замечания. Из-за этого для автора истинны и
 * {@link IsRemarkAuthorGuard}, и этот guard — что и стало причиной, по которой исходы
 * замечания пришлось различать намерением вызывающего кода
 * ({@link IsRemarkRejectionGuard} / {@link IsRemarkResolutionGuard}), а не правами.
 *
 * <p><b>Кода в спецификации нет.</b> Приписанный в V24 код {@code G-R-003} в
 * {@code 05_guards_actions_registry.md} §4.6 принадлежит условию
 * {@code ApproveOrApproveWithComments}.
 */
@Component("isRemarkAssigneeGuard")
public class IsRemarkAssigneeGuard implements Guard {

    /**
     * @return {@code true}, если инициатор перехода — ответственный за замечание
     *         ({@code remark.activatedBy})
     */
    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof Remark remark)) {
            return false;
        }

        return context.actorId() != null && context.actorId().equals(remark.getActivatedBy());
    }
}
