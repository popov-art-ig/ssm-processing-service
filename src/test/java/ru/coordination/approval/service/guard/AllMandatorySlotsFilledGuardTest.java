package ru.coordination.approval.service.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.ParticipantRole;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.engine.TransitionContext;

class AllMandatorySlotsFilledGuardTest {

    private final AllMandatorySlotsFilledGuard guard = new AllMandatorySlotsFilledGuard();

    @Test
    void passesWhenMandatoryStageHasParticipantInLatestIteration() {
        StageInstance stage = mandatoryStage(iteration(0, participant()), iteration(1, participant()));

        assertThat(evaluate(stage)).isTrue();
    }

    @Test
    void failsWhenMandatoryStageLatestIterationHasNoParticipants() {
        StageInstance stage = mandatoryStage(iteration(0, participant()), iteration(1));

        assertThat(evaluate(stage)).isFalse();
    }

    @Test
    void failsWhenMandatoryStageHasNoIterationsAtAll() {
        StageInstance stage = mandatoryStage();

        assertThat(evaluate(stage)).isFalse();
    }

    @Test
    void passesWhenNonMandatoryStageIsEmpty() {
        StageInstance stage = StageInstance.builder().mandatory(false).iterations(List.of()).build();

        assertThat(evaluate(stage)).isTrue();
    }

    private boolean evaluate(StageInstance stage) {
        ProcessInstance process = ProcessInstance.builder().stages(List.of(stage)).build();
        return guard.evaluate(new TransitionContext(process, UUID.randomUUID(), ActorType.USER, Map.of()));
    }

    private static StageInstance mandatoryStage(StageIteration... iterations) {
        return StageInstance.builder().mandatory(true).iterations(List.of(iterations)).build();
    }

    private static StageIteration iteration(int idx, Participant... participants) {
        return StageIteration.builder().iterationIdx(idx).participants(List.of(participants)).build();
    }

    private static Participant participant() {
        return Participant.builder().userId(UUID.randomUUID()).role(ParticipantRole.APPROVER).build();
    }
}
