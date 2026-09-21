package ru.coordination.approval.service.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.process.Remark;
import ru.coordination.approval.engine.TransitionContext;

class IsRemarkAssigneeGuardTest {

    private final IsRemarkAssigneeGuard guard = new IsRemarkAssigneeGuard();

    @Test
    void returnsTrueWhenActorIsAssignee() {
        UUID assigneeId = UUID.randomUUID();

        Remark remark = Remark.builder()
                .activatedBy(assigneeId)
                .createdAt(Instant.now())
                .build();

        TransitionContext context = new TransitionContext(remark, assigneeId, ActorType.USER, Map.of());

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsFalseWhenActorIsNotAssignee() {
        UUID assigneeId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        Remark remark = Remark.builder()
                .activatedBy(assigneeId)
                .createdAt(Instant.now())
                .build();

        TransitionContext context = new TransitionContext(remark, otherUserId, ActorType.USER, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseWhenActorIdIsNull() {
        Remark remark = Remark.builder()
                .activatedBy(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();

        TransitionContext context = new TransitionContext(remark, null, ActorType.USER, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseWhenEntityIsNotRemark() {
        String notARemark = "some string";
        TransitionContext context = new TransitionContext(notARemark, UUID.randomUUID(), ActorType.USER, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }
}
