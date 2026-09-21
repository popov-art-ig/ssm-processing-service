package ru.coordination.approval.service.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.DecisionMode;
import ru.coordination.approval.domain.common.StageType;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.ParticipantRole;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.engine.TransitionContext;

class AllParticipantsDecidedGuardTest {

    private final AllParticipantsDecidedGuard guard = new AllParticipantsDecidedGuard();

    @Test
    void returnsTrueWhenAllParticipantsDecided() {
        StageInstance stage = createStageWithParticipants(
                List.of("Decided", "Decided", "Decided"));

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsTrueWhenAllParticipantsDecidedOrAutoApproved() {
        StageInstance stage = createStageWithParticipants(
                List.of("Decided", "AutoApproved", "Decided"));

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsFalseWhenOneParticipantStillAssigned() {
        StageInstance stage = createStageWithParticipants(
                List.of("Decided", "Assigned", "Decided"));

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseWhenNoIterations() {
        StageInstance stage = StageInstance.builder()
                .stageType(StageType.APPROVAL)
                .duration(3)
                .status("Active")
                .createdAt(Instant.now())
                .build();

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseWhenNoParticipants() {
        StageInstance stage = StageInstance.builder()
                .stageType(StageType.APPROVAL)
                .duration(3)
                .status("Active")
                .createdAt(Instant.now())
                .build();

        StageIteration iteration = StageIteration.builder()
                .stage(stage)
                .iterationIdx(1)
                .status("Active")
                .startedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        stage.getIterations().add(iteration);

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseWhenEntityIsNotStageInstance() {
        String notAStage = "some string";

        TransitionContext context = new TransitionContext(notAStage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void usesLatestIterationWhenMultipleExist() {
        ProcessInstance process = ProcessInstance.builder()
                .createdAt(Instant.now())
                .build();

        StageInstance stage = StageInstance.builder()
                .process(process)
                .stageType(StageType.APPROVAL)
                .duration(3)
                .status("Active")
                .createdAt(Instant.now())
                .build();

        StageIteration oldIteration = StageIteration.builder()
                .stage(stage)
                .iterationIdx(1)
                .status("Completed")
                .startedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        Participant oldParticipant = Participant.builder()
                .stageIteration(oldIteration)
                .userId(UUID.randomUUID())
                .role(ParticipantRole.APPROVER)
                .status("Decided")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        oldIteration.getParticipants().add(oldParticipant);

        StageIteration newIteration = StageIteration.builder()
                .stage(stage)
                .iterationIdx(2)
                .status("Active")
                .startedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        Participant newParticipant = Participant.builder()
                .stageIteration(newIteration)
                .userId(UUID.randomUUID())
                .role(ParticipantRole.APPROVER)
                .status("Assigned")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        newIteration.getParticipants().add(newParticipant);

        stage.getIterations().add(oldIteration);
        stage.getIterations().add(newIteration);

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    private StageInstance createStageWithParticipants(List<String> statuses) {
        ProcessInstance process = ProcessInstance.builder()
                .createdAt(Instant.now())
                .build();

        StageInstance stage = StageInstance.builder()
                .process(process)
                .stageType(StageType.APPROVAL)
                .duration(3)
                .decisionMode(DecisionMode.AND)
                .status("Active")
                .createdAt(Instant.now())
                .build();

        StageIteration iteration = StageIteration.builder()
                .stage(stage)
                .iterationIdx(1)
                .status("Active")
                .startedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        for (String status : statuses) {
            Participant participant = Participant.builder()
                    .stageIteration(iteration)
                    .userId(UUID.randomUUID())
                    .role(ParticipantRole.APPROVER)
                    .status(status)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            iteration.getParticipants().add(participant);
        }

        stage.getIterations().add(iteration);
        return stage;
    }
}
