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

class IsTargetStageAllowedGuardTest {

    private final IsTargetStageAllowedGuard guard = new IsTargetStageAllowedGuard();

    @Test
    void returnsTrueWhenTargetStageIsValid() {
        ProcessInstance process = createProcessWithOnReworkStage(3);
        Map<String, Object> parameters = Map.of("targetStageOrderIdx", 2);
        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.USER, parameters);

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsTrueWhenTargetStageIsSameAsOnReworkStage() {
        ProcessInstance process = createProcessWithOnReworkStage(2);
        Map<String, Object> parameters = Map.of("targetStageOrderIdx", 2);
        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.USER, parameters);

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsTrueWhenTargetStageIsFirstStage() {
        ProcessInstance process = createProcessWithOnReworkStage(3);
        Map<String, Object> parameters = Map.of("targetStageOrderIdx", 1);
        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.USER, parameters);

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsFalseWhenTargetStageIsGreaterThanOnReworkStage() {
        ProcessInstance process = createProcessWithOnReworkStage(2);
        Map<String, Object> parameters = Map.of("targetStageOrderIdx", 3);
        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.USER, parameters);

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseWhenTargetStageIsLessThanOne() {
        ProcessInstance process = createProcessWithOnReworkStage(2);
        Map<String, Object> parameters = Map.of("targetStageOrderIdx", 0);
        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.USER, parameters);

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseWhenTargetStageOrderIdxIsNull() {
        ProcessInstance process = createProcessWithOnReworkStage(2);
        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.USER, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseWhenNoOnReworkStage() {
        ProcessInstance process = ProcessInstance.builder()
                .createdAt(Instant.now())
                .build();

        StageInstance stage = StageInstance.builder()
                .process(process)
                .orderIdx(1)
                .stageType(StageType.APPROVAL)
                .status("Active")
                .createdAt(Instant.now())
                .build();

        process.getStages().add(stage);

        Map<String, Object> parameters = Map.of("targetStageOrderIdx", 1);
        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.USER, parameters);

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseWhenEntityIsNotProcessInstance() {
        String notAProcess = "some string";
        Map<String, Object> parameters = Map.of("targetStageOrderIdx", 1);
        TransitionContext context = new TransitionContext(notAProcess, UUID.randomUUID(), ActorType.USER, parameters);

        assertThat(guard.evaluate(context)).isFalse();
    }

    private ProcessInstance createProcessWithOnReworkStage(int onReworkOrderIdx) {
        ProcessInstance process = ProcessInstance.builder()
                .createdAt(Instant.now())
                .build();

        for (int i = 1; i <= onReworkOrderIdx; i++) {
            StageInstance stage = StageInstance.builder()
                    .process(process)
                    .orderIdx(i)
                    .stageType(StageType.APPROVAL)
                    .status(i == onReworkOrderIdx ? "OnRework" : "Approved")
                    .createdAt(Instant.now())
                    .build();
            process.getStages().add(stage);
        }

        return process;
    }
}
