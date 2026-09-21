package ru.coordination.approval.service.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.DecisionMode;
import ru.coordination.approval.domain.common.StageType;
import ru.coordination.approval.domain.process.Decision;
import ru.coordination.approval.domain.process.DecisionRepository;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.ParticipantRole;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.engine.TransitionContext;

class AggregationApprovedWithCommentsGuardTest {

    private DecisionRepository decisionRepository;
    private AggregationApprovedWithCommentsGuard guard;

    @BeforeEach
    void setUp() {
        decisionRepository = mock(DecisionRepository.class);
        guard = new AggregationApprovedWithCommentsGuard(decisionRepository);
    }

    @Test
    void returnsTrueForAndModeWithApproveAndApproveWithComments() {
        StageInstance stage = createStageWithDecisions(
                DecisionMode.AND,
                List.of("APPROVE", "APPROVE_WITH_COMMENTS", "APPROVE"));

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsFalseForAndModeWithAllApproveNoComments() {
        StageInstance stage = createStageWithDecisions(
                DecisionMode.AND,
                List.of("APPROVE", "APPROVE", "APPROVE"));

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseForAndModeWithOneReject() {
        StageInstance stage = createStageWithDecisions(
                DecisionMode.AND,
                List.of("APPROVE_WITH_COMMENTS", "REJECT", "APPROVE"));

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsTrueForAnyApproveModeWithApproveWithComments() {
        StageInstance stage = createStageWithDecisions(
                DecisionMode.ANY_APPROVE,
                List.of("REJECT", "APPROVE_WITH_COMMENTS", "REJECT"));

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsFalseForAnyApproveModeWithOnlyApprove() {
        StageInstance stage = createStageWithDecisions(
                DecisionMode.ANY_APPROVE,
                List.of("REJECT", "APPROVE", "REJECT"));

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseForAnyApproveModeWithAllReject() {
        StageInstance stage = createStageWithDecisions(
                DecisionMode.ANY_APPROVE,
                List.of("REJECT", "REJECT", "REJECT"));

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsTrueForAnyRejectModeWithApproveWithCommentsAndNoReject() {
        StageInstance stage = createStageWithDecisions(
                DecisionMode.ANY_REJECT,
                List.of("APPROVE", "APPROVE_WITH_COMMENTS", "APPROVE"));

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsFalseForAnyRejectModeWithOneReject() {
        StageInstance stage = createStageWithDecisions(
                DecisionMode.ANY_REJECT,
                List.of("APPROVE_WITH_COMMENTS", "REJECT", "APPROVE"));

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsTrueForAnyDecisionModeWithApproveWithComments() {
        StageInstance stage = createStageWithDecisions(
                DecisionMode.ANY_DECISION,
                List.of("REJECT", "APPROVE_WITH_COMMENTS", "REJECT"));

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsFalseForAnyDecisionModeWithoutApproveWithComments() {
        StageInstance stage = createStageWithDecisions(
                DecisionMode.ANY_DECISION,
                List.of("REJECT", "APPROVE", "REJECT"));

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsTrueForFirstRejectFailFastModeWithApproveWithComments() {
        StageInstance stage = createStageWithDecisions(
                DecisionMode.FIRST_REJECT_FAIL_FAST,
                List.of("APPROVE", "APPROVE_WITH_COMMENTS", "APPROVE"));

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsFalseForFirstRejectFailFastModeWithOneReject() {
        StageInstance stage = createStageWithDecisions(
                DecisionMode.FIRST_REJECT_FAIL_FAST,
                List.of("APPROVE_WITH_COMMENTS", "REJECT", "APPROVE"));

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void defaultsToAndModeWhenDecisionModeIsNull() {
        StageInstance stage = createStageWithDecisions(
                null,
                List.of("APPROVE", "APPROVE_WITH_COMMENTS", "APPROVE"));

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsFalseWhenNoIterations() {
        StageInstance stage = StageInstance.builder()
                .stageType(StageType.APPROVAL)
                .duration(3)
                .decisionMode(DecisionMode.AND)
                .status("Active")
                .createdAt(Instant.now())
                .build();

        TransitionContext context = new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseWhenEntityIsNotStageInstance() {
        String notAStage = "some string";

        TransitionContext context = new TransitionContext(notAStage, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    private StageInstance createStageWithDecisions(DecisionMode mode, List<String> decisionResults) {
        ProcessInstance process = ProcessInstance.builder()
                .createdAt(Instant.now())
                .build();

        StageInstance stage = StageInstance.builder()
                .process(process)
                .stageType(StageType.APPROVAL)
                .duration(3)
                .decisionMode(mode)
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

        for (int i = 0; i < decisionResults.size(); i++) {
            Participant participant = Participant.builder()
                    .id(UUID.randomUUID())
                    .stageIteration(iteration)
                    .userId(UUID.randomUUID())
                    .role(ParticipantRole.APPROVER)
                    .status("Decided")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            iteration.getParticipants().add(participant);

            Decision decision = Decision.builder()
                    .participant(participant)
                    .result(decisionResults.get(i))
                    .auto(false)
                    .recordedAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();

            when(decisionRepository.findByParticipantId(participant.getId()))
                    .thenReturn(java.util.Optional.of(decision));
        }

        stage.getIterations().add(iteration);
        return stage;
    }
}
