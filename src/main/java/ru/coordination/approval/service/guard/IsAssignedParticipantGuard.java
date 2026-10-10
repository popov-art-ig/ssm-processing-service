package ru.coordination.approval.service.guard;

import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-PA-001 «Является участником» — предусловие перехода {@code Decide}
 * ({@code Assigned → Decided}, {@code USER_ACTION}) участника
 * ({@code 05_guards_actions_registry.md} §4.3). Резолвится {@code guard_registry.handler =
 * 'isAssignedParticipantGuard'} (миграция V20, код реестра {@code IsAssignedParticipant}).
 *
 * <p><b>Контракт.</b> {@code true} — инициатор перехода ({@code TransitionContext.actorId()})
 * совпадает с {@code participant.userId} и участник находится в статусе {@code Assigned};
 * {@code false} — иначе (в том числе для системного триггера без {@code actorId}: тогда
 * {@code actorId} равен {@code null} и решение нельзя приписать конкретному пользователю).
 * Побочных эффектов нет.
 *
 * <p><b>Почему статус проверяется в guard'е, хотя переход и так объявлен из {@code Assigned}.</b>
 * {@code transition_config.fromState} отсекает участника, находящегося в другом состоянии, только
 * если движок видит актуальный статус сущности. При вызове через
 * {@code DecisionService.decide} статус приходит из загруженного {@link Participant}, и явное
 * условие здесь делает отказ детерминированным (а сервис дополнительно проверяет статус и
 * бросает информативное исключение вместо тихого {@code notPerformed}).
 *
 * <p>Guard проверяет только личность назначенного участника. Ролевые проверки спецификации
 * ({@code G-H-001 IsMainApprover}, {@code G-H-002 IsAdditionalApprover} §4.5) не реализованы:
 * роли доп. согласующих в этот переход не включены.
 */
@Component("isAssignedParticipantGuard")
public class IsAssignedParticipantGuard implements Guard {

    /**
     * @return {@code true}, если решение принимает назначенный участник в статусе
     *         {@code Assigned}
     */
    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof Participant participant)) {
            return false;
        }

        return context.actorId() != null
                && context.actorId().equals(participant.getUserId())
                && "Assigned".equals(participant.getStatus());
    }
}
