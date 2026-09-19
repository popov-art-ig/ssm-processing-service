package ru.coordination.approval.service.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.ExecutionOrder;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.ParticipantRole;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.engine.TransitionContext;

class AssignParticipantTasksActionTest {

    private final AssignParticipantTasksAction action = new AssignParticipantTasksAction();

    @Test
    void assignsAllParticipantsWhenParallel() {
        Participant p1 = participant(0);
        Participant p2 = participant(1);

        action.execute(context(stageWithParticipants(ExecutionOrder.PARALLEL, p1, p2)));

        assertThat(p1.getStatus()).isEqualTo("Assigned");
        assertThat(p1.getAssignedAt()).isNotNull();
        assertThat(p2.getStatus()).isEqualTo("Assigned");
        assertThat(p2.getAssignedAt()).isNotNull();
    }

    @Test
    void treatsNullExecutionOrderAsParallel() {
        Participant p1 = participant(0);

        action.execute(context(stageWithParticipants(null, p1)));

        assertThat(p1.getStatus()).isEqualTo("Assigned");
        assertThat(p1.getAssignedAt()).isNotNull();
    }

    @Test
    void assignsOnlyFirstParticipantByOrderIdxWhenSequential() {
        Participant first = participant(0);
        Participant second = participant(1);

        // Passed out of orderIdx order on purpose - action must sort, not rely on list order.
        action.execute(context(stageWithParticipants(ExecutionOrder.SEQUENTIAL, second, first)));

        assertThat(first.getStatus()).isEqualTo("Assigned");
        assertThat(first.getAssignedAt()).isNotNull();
        assertThat(second.getStatus()).isEqualTo("Pending");
        assertThat(second.getAssignedAt()).isNull();
    }

    @Test
    void doesNothingWhenCurrentIterationHasNoParticipants() {
        StageInstance stage = stageWithParticipants(ExecutionOrder.PARALLEL);

        action.execute(context(stage));
    }

    @Test
    void throwsWhenStageHasNoIterationsAtAll() {
        StageInstance stage = StageInstance.builder().iterations(List.of()).build();

        assertThrows(IllegalStateException.class, () -> action.execute(context(stage)));
    }

    private static TransitionContext context(StageInstance stage) {
        return new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());
    }

    private static StageInstance stageWithParticipants(ExecutionOrder executionOrder, Participant... participants) {
        StageIteration iteration = StageIteration.builder()
                .iterationIdx(0)
                .participants(List.of(participants))
                .build();
        return StageInstance.builder()
                .executionOrder(executionOrder)
                .iterations(List.of(iteration))
                .build();
    }

    private static Participant participant(int orderIdx) {
        return Participant.builder()
                .userId(UUID.randomUUID())
                .role(ParticipantRole.APPROVER)
                .orderIdx(orderIdx)
                .status("Pending")
                .build();
    }
}
