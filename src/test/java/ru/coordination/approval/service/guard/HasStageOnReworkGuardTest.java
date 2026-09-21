package ru.coordination.approval.service.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.StageType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.engine.TransitionContext;

class HasStageOnReworkGuardTest {

    private final HasStageOnReworkGuard guard = new HasStageOnReworkGuard();

    @Test
    void returnsTrueWhenAtLeastOneStageIsOnRework() {
        ProcessInstance process = ProcessInstance.builder()
                .createdAt(Instant.now())
                .build();

        StageInstance stage1 = StageInstance.builder()
                .process(process)
                .orderIdx(1)
                .stageType(StageType.APPROVAL)
                .status("Approved")
                .createdAt(Instant.now())
                .build();

        StageInstance stage2 = StageInstance.builder()
                .process(process)
                .orderIdx(2)
                .stageType(StageType.APPROVAL)
                .status("OnRework")
                .createdAt(Instant.now())
                .build();

        process.getStages().add(stage1);
        process.getStages().add(stage2);

        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsFalseWhenNoStageIsOnRework() {
        ProcessInstance process = ProcessInstance.builder()
                .createdAt(Instant.now())
                .build();

        StageInstance stage1 = StageInstance.builder()
                .process(process)
                .orderIdx(1)
                .stageType(StageType.APPROVAL)
                .status("Approved")
                .createdAt(Instant.now())
                .build();

        StageInstance stage2 = StageInstance.builder()
                .process(process)
                .orderIdx(2)
                .stageType(StageType.APPROVAL)
                .status("Active")
                .createdAt(Instant.now())
                .build();

        process.getStages().add(stage1);
        process.getStages().add(stage2);

        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseWhenAllStagesAreApproved() {
        ProcessInstance process = ProcessInstance.builder()
                .createdAt(Instant.now())
                .build();

        StageInstance stage1 = StageInstance.builder()
                .process(process)
                .orderIdx(1)
                .stageType(StageType.APPROVAL)
                .status("Approved")
                .createdAt(Instant.now())
                .build();

        process.getStages().add(stage1);

        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseWhenEntityIsNotProcessInstance() {
        String notAProcess = "some string";
        TransitionContext context = new TransitionContext(notAProcess, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }
}
