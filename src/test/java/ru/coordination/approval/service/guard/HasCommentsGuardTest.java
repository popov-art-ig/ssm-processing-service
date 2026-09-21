package ru.coordination.approval.service.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.engine.TransitionContext;

class HasCommentsGuardTest {

    private HasCommentsGuard guard;

    @BeforeEach
    void setUp() {
        guard = new HasCommentsGuard();
    }

    @Test
    void returnsTrueWhenAtLeastOneStageApprovedWithComments() {
        ProcessInstance process = createProcessWithStages(
                "Approved", "ApprovedWithComments", "Approved");

        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsTrueWhenAllStagesApprovedWithComments() {
        ProcessInstance process = createProcessWithStages(
                "ApprovedWithComments", "ApprovedWithComments", "ApprovedWithComments");

        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsTrueWhenFirstStageHasComments() {
        ProcessInstance process = createProcessWithStages("ApprovedWithComments", "Approved", "Approved");

        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsTrueWhenLastStageHasComments() {
        ProcessInstance process = createProcessWithStages("Approved", "Approved", "ApprovedWithComments");

        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsFalseWhenAllStagesApproved() {
        ProcessInstance process = createProcessWithStages("Approved", "Approved", "Approved");

        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseWhenStagesArePending() {
        ProcessInstance process = createProcessWithStages("Pending", "Pending", "Pending");

        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseWhenStagesAreOnRework() {
        ProcessInstance process = createProcessWithStages("OnRework", "Approved", "Approved");

        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseWhenProcessHasNoStages() {
        ProcessInstance process = ProcessInstance.builder()
                .id(UUID.randomUUID())
                .processType(ProcessType.STANDARD)
                .configVersion(1)
                .status("InProgress")
                .initiatorId(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();

        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseWhenMixOfApprovedAndCompleted() {
        ProcessInstance process = createProcessWithStages("Approved", "Completed", "Approved");

        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    private ProcessInstance createProcessWithStages(String... statuses) {
        ProcessInstance process = ProcessInstance.builder()
                .id(UUID.randomUUID())
                .processType(ProcessType.STANDARD)
                .configVersion(1)
                .status("InProgress")
                .initiatorId(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();

        int orderIdx = 1;
        for (String status : statuses) {
            StageInstance stage = StageInstance.builder()
                    .id(UUID.randomUUID())
                    .process(process)
                    .orderIdx(orderIdx++)
                    .status(status)
                    .createdAt(Instant.now())
                    .build();
            process.getStages().add(stage);
        }

        return process;
    }
}
