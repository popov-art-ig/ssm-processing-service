package ru.coordination.approval.service.action;

import java.time.Instant;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.Remark;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

/**
 * A-R-001 «Пометить действующим»: назначает ответственным за обработку автора замечания
 * (PHASE-11). Резолвится {@code action_registry.handler = 'assignRemarkToAuthorAction'} (миграция
 * V24, код реестра {@code AssignRemarkToAuthor}); включён в actions перехода {@code CreateRemark}
 * ({@code Draft → Active}, {@code USER_ACTION}), всегда перед
 * {@link NotifyRemarkStatusChangeAction}.
 *
 * <p><b>Какое состояние меняет.</b> {@code remark.activatedBy = remark.authorId} и
 * {@code remark.activatedAt = now}. Статус {@code Active} записывает не это действие, а
 * {@code TransitionEngine} по {@code to_state} перехода; действие лишь фиксирует, кто отвечает за
 * обработку. Явного {@code save()} нет — сущность managed в транзакции движка.
 *
 * <p><b>Почему автор назначен сам себе.</b> Замечание создаёт участник, он же обязан его
 * обработать или отклонить, поэтому ответственный и автор совпадают. Побочное следствие —
 * guard'ы {@link ru.coordination.approval.service.guard.IsRemarkAuthorGuard} и
 * {@link ru.coordination.approval.service.guard.IsRemarkAssigneeGuard} истинны для одного и того
 * же пользователя, из-за чего исход обработки различается парой намерений
 * ({@link ru.coordination.approval.service.guard.IsRemarkRejectionGuard} /
 * {@link ru.coordination.approval.service.guard.IsRemarkResolutionGuard}), а не правами.
 * Переназначение замечания другому исполнителю (например, ответственному процесса) в этом
 * действии не реализовано.
 *
 * <p>Кода {@code A-R-001} в спецификации соответствует действие {@code MarkActivated} §5.7 —
 * «переводит замечание в Active»; запись {@code activatedBy} в спецификации не описана.
 */
@Component("assignRemarkToAuthorAction")
public class AssignRemarkToAuthorAction implements Action {

    /** Записывает в {@code remark.activatedBy} автора замечания и фиксирует {@code activatedAt}. */
    @Override
    public void execute(TransitionContext context) {
        Remark remark = (Remark) context.entity();
        remark.setActivatedBy(remark.getAuthorId());
        remark.setActivatedAt(Instant.now());
    }
}
