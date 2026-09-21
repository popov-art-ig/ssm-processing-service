package ru.coordination.approval.service.action;

import java.time.Instant;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.Remark;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

/**
 * A-R-001 «Назначить замечание автору на обработку» (PHASE-11).
 * Устанавливает assigneeId = authorId и assignedAt при создании замечания.
 */
@Component("assignRemarkToAuthorAction")
public class AssignRemarkToAuthorAction implements Action {

    @Override
    public void execute(TransitionContext context) {
        Remark remark = (Remark) context.entity();
        remark.setActivatedBy(remark.getAuthorId());
        remark.setActivatedAt(Instant.now());
    }
}
