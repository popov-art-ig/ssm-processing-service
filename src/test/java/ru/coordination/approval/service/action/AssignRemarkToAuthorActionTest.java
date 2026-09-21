package ru.coordination.approval.service.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.process.Remark;
import ru.coordination.approval.engine.TransitionContext;

class AssignRemarkToAuthorActionTest {

    private final AssignRemarkToAuthorAction action = new AssignRemarkToAuthorAction();

    @Test
    void assignsRemarkToAuthor() {
        UUID authorId = UUID.randomUUID();

        Remark remark = Remark.builder()
                .id(UUID.randomUUID())
                .authorId(authorId)
                .activatedBy(null)
                .activatedAt(null)
                .createdAt(Instant.now())
                .build();

        TransitionContext context = new TransitionContext(remark, authorId, ActorType.USER, Map.of());

        action.execute(context);

        assertThat(remark.getActivatedBy()).isEqualTo(authorId);
        assertThat(remark.getActivatedAt()).isNotNull();
    }

    @Test
    void updatesAssigneeEvenIfAlreadySet() {
        UUID authorId = UUID.randomUUID();
        UUID oldAssigneeId = UUID.randomUUID();

        Remark remark = Remark.builder()
                .id(UUID.randomUUID())
                .authorId(authorId)
                .activatedBy(oldAssigneeId)
                .activatedAt(Instant.now().minusSeconds(3600))
                .createdAt(Instant.now())
                .build();

        TransitionContext context = new TransitionContext(remark, authorId, ActorType.USER, Map.of());

        action.execute(context);

        assertThat(remark.getActivatedBy()).isEqualTo(authorId);
        assertThat(remark.getActivatedAt()).isAfter(Instant.now().minusSeconds(10));
    }
}
