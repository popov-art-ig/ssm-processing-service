package ru.coordination.approval.service.action;

import org.springframework.stereotype.Component;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

/**
 * <b>Заглушка</b> действия «уведомить об изменении статуса замечания» (PHASE-11). Резолвится
 * {@code action_registry.handler = 'notifyRemarkStatusChangeAction'}
 * (миграция V24, код реестра {@code NotifyRemarkStatusChange}) и включён в actions <i>всех</i>
 * переходов жизненного цикла замечания: {@code CreateRemark}, {@code StartProcessingRemark},
 * {@code ProcessRemark}, {@code RejectRemark}, {@code MarkNotRequired}.
 *
 * <p><b>Что именно не реализовано.</b> {@code execute} — пустой метод: уведомления об изменении
 * статуса замечания не отправляются, событие в Outbox не публикуется. В описании реестра V24 это
 * зафиксировано как «PHASE-11: заглушка», а реализация отнесена к Event Publisher
 * (PHASE-19, {@code doc/REMAINING_PHASES.md} §«PHASE-19: Event Publisher»). До этой фазы переходы
 * замечания меняют только статус сущности.
 *
 * <p><b>Кода в спецификации нет.</b> В {@code 05_guards_actions_registry.md} уведомления описаны
 * как {@code A-P-005 NotifyParticipants} (§5.1) с параметрами {@code recipients}/{@code template};
 * отдельного действия «уведомить об изменении статуса замечания» в §5.7 нет — события
 * {@code approval.remark.*} перечислены в {@code 06_event_contract.md} §10 и должны публиковаться
 * через {@code transition_config.emits}, а не этим действием.
 *
 * <p>Действие оставлено в карте осознанно: оно фиксирует место будущей публикации события и
 * сохраняет список {@code actions} переходов неизменным, когда реализация появится.
 */
@Component("notifyRemarkStatusChangeAction")
public class NotifyRemarkStatusChangeAction implements Action {

    /** No-op: публикация события об изменении статуса замечания появится в PHASE-19. */
    @Override
    public void execute(TransitionContext context) {
        // no-op: заглушка для PHASE-11
    }
}
