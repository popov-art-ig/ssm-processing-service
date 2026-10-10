package ru.coordination.approval.service.guard;

import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.Remark;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * Guard «Текущий пользователь — автор замечания» (PHASE-11). Резолвится
 * {@code guard_registry.handler = 'isRemarkAuthorGuard'} (миграция V24, код реестра
 * {@code IsRemarkAuthor}). {@code true} — {@code context.actorId()} совпадает с
 * {@code remark.authorId}; {@code false} — иначе, включая системный триггер без {@code actorId}.
 * Побочных эффектов нет.
 *
 * <p><b>Ни в один переход не включён.</b> Поиск по {@code transition_config.guards} во всех
 * миграциях не находит {@code IsRemarkAuthor}: guard зарегистрирован в реестре и покрыт тестом,
 * но движок его не вызывает. По замыслу PHASE-11 он должен был участвовать в закрытии и
 * отклонении замечания автором; в итоговой карте V24 исходы {@code ProcessRemark} и
 * {@code RejectRemark} различаются не этим guard'ом, а парой намерений
 * {@link IsRemarkResolutionGuard} / {@link IsRemarkRejectionGuard}.
 *
 * <p><b>Кода в спецификации нет.</b> Приписанный в V24 код {@code G-R-002} в
 * {@code 05_guards_actions_registry.md} §4.6 принадлежит условию {@code HasReason} (проверка
 * {@code rejectionReason}), а §4.3 описывает участника процесса, а не автора замечания.
 */
@Component("isRemarkAuthorGuard")
public class IsRemarkAuthorGuard implements Guard {

    /**
     * @return {@code true}, если инициатор перехода — автор замечания ({@code remark.authorId})
     */
    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof Remark remark)) {
            return false;
        }

        return context.actorId() != null && context.actorId().equals(remark.getAuthorId());
    }
}
