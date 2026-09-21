package ru.coordination.approval.service.action;

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

class RejectStagesFromActionTest {

    private final RejectStagesFromAction action = new RejectStagesFromAction();

    @Test
    void rejectsStagesBetweenTargetAndOnRework() {
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
                .status("Approved")
                .createdAt(Instant.now())
                .build();

        StageInstance stage3 = StageInstance.builder()
                .process(process)
                .orderIdx(3)
                .stageType(StageType.APPROVAL)
                .status("OnRework")
                .createdAt(Instant.now())
                .build();

        process.getStages().add(stage1);
        process.getStages().add(stage2);
        process.getStages().add(stage3);

        Map<String, Object> parameters = Map.of("targetStageOrderIdx", 1);
        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.USER, parameters);

        action.execute(context);

        assertThat(stage1.getStatus()).isEqualTo("Approved");
        assertThat(stage2.getStatus()).isEqualTo("Rejected");
        assertThat(stage3.getStatus()).isEqualTo("Rejected");
    }

    @Test
    void doesNotRejectTargetStage() {
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

        Map<String, Object> parameters = Map.of("targetStageOrderIdx", 1);
        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.USER, parameters);

        action.execute(context);

        assertThat(stage1.getStatus()).isEqualTo("Approved");
        assertThat(stage2.getStatus()).isEqualTo("Rejected");
    }

    @Test
    void noStagesRejectedWhenTargetIsSameAsOnRework() {
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

        Map<String, Object> parameters = Map.of("targetStageOrderIdx", 2);
        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.USER, parameters);

        action.execute(context);

        assertThat(stage1.getStatus()).isEqualTo("Approved");
        assertThat(stage2.getStatus()).isEqualTo("OnRework");
    }
}
