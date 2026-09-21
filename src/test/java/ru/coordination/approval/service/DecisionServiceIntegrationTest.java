package ru.coordination.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.coordination.approval.domain.common.ExecutionOrder;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.common.StageType;
import ru.coordination.approval.domain.process.Comment;
import ru.coordination.approval.domain.process.CommentRepository;
import ru.coordination.approval.domain.process.Decision;
import ru.coordination.approval.domain.process.DecisionRepository;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.ParticipantRepository;
import ru.coordination.approval.domain.process.ParticipantRole;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.ProcessRepository;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;
import ru.coordination.approval.engine.TransitionResult;

/**
 * Интеграционный тест {@link DecisionService} (Testcontainers) — реальные миграции
 * {@code V1}-{@code V20}. Критерии приёмки PHASE-05 (1-8).
 */
@SpringBootTest
@ActiveProfiles("test")
class DecisionServiceIntegrationTest {

    @Autowired
    private DecisionService decisionService;

    @Autowired
    private ProcessService processService;

    @Autowired
    private ProcessRepository processRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private DecisionRepository decisionRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Test
    void decidesWithApprove() {
        TestFixture fixture = prepareProcessWithAssignedParticipant();
        UUID participantId = fixture.participantId();
        UUID actorId = fixture.actorId();

        TransitionResult result = decisionService.decide(
                fixture.processId(),
                fixture.stageId(),
                participantId,
                "APPROVE",
                null,
                actorId);

        assertThat(result.performed()).isTrue();
        assertThat(result.toState()).isEqualTo("Decided");
        assertThat(result.emittedEvents()).containsExactly("approval.decision.recorded");

        Participant participant = participantRepository.findById(participantId).orElseThrow();
        assertThat(participant.getStatus()).isEqualTo("Decided");

        List<Decision> decisions = decisionRepository.findAll();
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).getParticipant().getId()).isEqualTo(participantId);
        assertThat(decisions.get(0).getResult()).isEqualTo("APPROVE");
        assertThat(decisions.get(0).getComment()).isNull();
        assertThat(decisions.get(0).isAuto()).isFalse();
        assertThat(decisions.get(0).getRecordedAt()).isNotNull();
    }

    @Test
    void decidesWithApproveWithComments() {
        TestFixture fixture = prepareProcessWithAssignedParticipant();
        UUID participantId = fixture.participantId();
        UUID actorId = fixture.actorId();

        TransitionResult result = decisionService.decide(
                fixture.processId(),
                fixture.stageId(),
                participantId,
                "APPROVE_WITH_COMMENTS",
                "Looks good overall",
                actorId);

        assertThat(result.performed()).isTrue();
        assertThat(result.toState()).isEqualTo("Decided");

        Participant participant = participantRepository.findById(participantId).orElseThrow();
        assertThat(participant.getStatus()).isEqualTo("Decided");

        List<Decision> decisions = decisionRepository.findAll();
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).getResult()).isEqualTo("APPROVE_WITH_COMMENTS");
        assertThat(decisions.get(0).getComment()).isEqualTo("Looks good overall");

        List<Comment> comments = commentRepository.findAll();
        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).getText()).isEqualTo("Looks good overall");
        assertThat(comments.get(0).getParticipant().getId()).isEqualTo(participantId);
        assertThat(comments.get(0).getAuthorId()).isEqualTo(actorId);
    }

    @Test
    void decidesWithReject() {
        TestFixture fixture = prepareProcessWithAssignedParticipant();
        UUID participantId = fixture.participantId();
        UUID actorId = fixture.actorId();

        TransitionResult result = decisionService.decide(
                fixture.processId(),
                fixture.stageId(),
                participantId,
                "REJECT",
                "Needs major changes",
                actorId);

        assertThat(result.performed()).isTrue();
        assertThat(result.toState()).isEqualTo("Decided");

        Participant participant = participantRepository.findById(participantId).orElseThrow();
        assertThat(participant.getStatus()).isEqualTo("Decided");

        List<Decision> decisions = decisionRepository.findAll();
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).getResult()).isEqualTo("REJECT");
        assertThat(decisions.get(0).getComment()).isEqualTo("Needs major changes");

        List<Comment> comments = commentRepository.findAll();
        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).getText()).isEqualTo("Needs major changes");
    }

    @Test
    void throwsExceptionWhenApproveWithCommentsHasNoComment() {
        TestFixture fixture = prepareProcessWithAssignedParticipant();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                decisionService.decide(
                        fixture.processId(),
                        fixture.stageId(),
                        fixture.participantId(),
                        "APPROVE_WITH_COMMENTS",
                        null,
                        fixture.actorId()));

        assertThat(exception.getMessage()).contains("Comment is required for APPROVE_WITH_COMMENTS");

        // Verify no decision was created
        assertThat(decisionRepository.findAll()).isEmpty();
    }

    @Test
    void throwsExceptionWhenRejectHasNoComment() {
        TestFixture fixture = prepareProcessWithAssignedParticipant();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                decisionService.decide(
                        fixture.processId(),
                        fixture.stageId(),
                        fixture.participantId(),
                        "REJECT",
                        "",
                        fixture.actorId()));

        assertThat(exception.getMessage()).contains("Comment is required for REJECT");

        assertThat(decisionRepository.findAll()).isEmpty();
    }

    @Test
    void doesNotDecideWhenActorIsNotParticipantUser() {
        TestFixture fixture = prepareProcessWithAssignedParticipant();
        UUID differentActorId = UUID.randomUUID();

        TransitionResult result = decisionService.decide(
                fixture.processId(),
                fixture.stageId(),
                fixture.participantId(),
                "APPROVE",
                null,
                differentActorId);

        assertThat(result.performed()).isFalse();

        Participant participant = participantRepository.findById(fixture.participantId()).orElseThrow();
        assertThat(participant.getStatus()).isEqualTo("Assigned");

        assertThat(decisionRepository.findAll()).isEmpty();
    }

    @Test
    void doesNotDecideWhenParticipantStatusIsNotAssigned() {
        TestFixture fixture = prepareProcessWithAssignedParticipant();

        // Manually set participant status to Pending
        Participant participant = participantRepository.findById(fixture.participantId()).orElseThrow();
        participant.setStatus("Pending");
        participantRepository.save(participant);

        TransitionResult result = decisionService.decide(
                fixture.processId(),
                fixture.stageId(),
                fixture.participantId(),
                "APPROVE",
                null,
                fixture.actorId());

        assertThat(result.performed()).isFalse();

        participant = participantRepository.findById(fixture.participantId()).orElseThrow();
        assertThat(participant.getStatus()).isEqualTo("Pending");

        assertThat(decisionRepository.findAll()).isEmpty();
    }

    private record TestFixture(UUID processId, UUID stageId, UUID participantId, UUID actorId) {
    }

    private TestFixture prepareProcessWithAssignedParticipant() {
        UUID initiatorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Template template = templateRepository.save(Template.builder()
                .id(UUID.randomUUID())
                .name("Test template " + UUID.randomUUID())
                .processType(ProcessType.STANDARD)
                .status(LifecycleStatus.PUBLISHED)
                .createdAt(Instant.now())
                .createdBy(UUID.randomUUID())
                .updatedAt(Instant.now())
                .build());

        Instant now = Instant.now();
        ProcessInstance process = ProcessInstance.builder()
                .id(UUID.randomUUID())
                .entityType("DOCUMENT")
                .entityId(UUID.randomUUID())
                .templateRef(template.getId())
                .processType(ProcessType.STANDARD)
                .configVersion(1)
                .status("Draft")
                .initiatorId(initiatorId)
                .createdAt(now)
                .stages(new ArrayList<>())
                .build();

        StageInstance stage = StageInstance.builder()
                .id(UUID.randomUUID())
                .process(process)
                .orderIdx(0)
                .originalOrderIdx(0)
                .stageType(StageType.APPROVAL)
                .duration(3)
                .mandatory(true)
                .executionOrder(ExecutionOrder.PARALLEL)
                .status("Pending")
                .createdAt(now)
                .build();

        StageIteration iteration = StageIteration.builder()
                .id(UUID.randomUUID())
                .stage(stage)
                .iterationIdx(0)
                .status("Active")
                .startedAt(now)
                .createdAt(now)
                .build();

        Participant participant = Participant.builder()
                .id(UUID.randomUUID())
                .stageIteration(iteration)
                .userId(userId)
                .role(ParticipantRole.APPROVER)
                .orderIdx(0)
                .status("Pending")
                .createdAt(now)
                .updatedAt(now)
                .build();

        iteration.setParticipants(List.of(participant));
        stage.setIterations(List.of(iteration));
        process.setStages(List.of(stage));

        ProcessInstance savedProcess = processRepository.save(process);

        // Start process and activate first stage
        processService.startProcess(savedProcess.getId(), initiatorId);

        // Reload to get updated participant status (should be Assigned after activation)
        Participant assignedParticipant = participantRepository.findById(participant.getId()).orElseThrow();

        return new TestFixture(
                savedProcess.getId(),
                stage.getId(),
                assignedParticipant.getId(),
                userId);
    }
}
