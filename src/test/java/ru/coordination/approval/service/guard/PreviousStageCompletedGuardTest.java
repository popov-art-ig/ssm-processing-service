package ru.coordination.approval.service.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.engine.TransitionContext;

class PreviousStageCompletedGuardTest {

    private final PreviousStageCompletedGuard guard = new PreviousStageCompletedGuard();

    @Test
    void passesWhenNoPreviousStage() {
        StageInstance first = stage(0, "Active");
        wireProcess(first);

        assertThat(evaluate(first)).isTrue();
    }

    @Test
    void passesWhenPreviousStageIsApproved() {
        assertThat(evaluateWithPreviousStatus("Approved")).isTrue();
    }

    @Test
    void passesWhenPreviousStageIsApprovedWithComments() {
        assertThat(evaluateWithPreviousStatus("ApprovedWithComments")).isTrue();
    }

    @Test
    void passesWhenPreviousStageIsCompleted() {
        assertThat(evaluateWithPreviousStatus("Completed")).isTrue();
    }

    @Test
    void failsWhenPreviousStageIsNotInAFinalStatus() {
        assertThat(evaluateWithPreviousStatus("Active")).isFalse();
    }

    private boolean evaluateWithPreviousStatus(String previousStatus) {
        StageInstance previous = stage(0, previousStatus);
        StageInstance second = stage(1, "Pending");
        wireProcess(previous, second);
        return evaluate(second);
    }

    private boolean evaluate(StageInstance stage) {
        return guard.evaluate(new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of()));
    }

    private static StageInstance stage(int orderIdx, String status) {
        return StageInstance.builder().orderIdx(orderIdx).status(status).build();
    }

    private static void wireProcess(StageInstance... stages) {
        ProcessInstance process = ProcessInstance.builder().stages(List.of(stages)).build();
        for (StageInstance stage : stages) {
            stage.setProcess(process);
        }
    }
}
