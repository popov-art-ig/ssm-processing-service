package ru.coordination.approval.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.coordination.approval.domain.common.DecisionMode;
import ru.coordination.approval.domain.common.ExecutionOrder;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.common.StageType;
import ru.coordination.approval.domain.process.ParticipantRepository;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.ProcessRepository;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.ParticipantRole;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;

/**
 * Интеграционный тест агрегации решений (PHASE-06) — реальные миграции {@code V1}-{@code V21}.
 * Критерии приёмки PHASE-06 (1-8).
 */
@SpringBootTest
@ActiveProfiles("test")
class StageAggregationIntegrationTest {

    @Autowired
    private DecisionService decisionService;

    @Autowired
    private ProcessService processService;

    @Autowired
    private ProcessRepository processRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Test
    void andModeWithAllApprove_stageBecomesApproved() {
        TestFixture fixture = prepareProcessWithParticipants(DecisionMode.AND, 3);

        fixture.participants().get(0).decide("APPROVE");
        fixture.participants().get(1).decide("APPROVE");

        StageInstance stage = processRepository.findById(fixture.processId())
                .orElseThrow()
                .getStages().get(0);
        assertThat(stage.getStatus()).isEqualTo("Active");

        fixture.participants().get(2).decide("APPROVE");

        stage = processRepository.findById(fixture.processId())
                .orElseThrow()
                .getStages().get(0);
        assertThat(stage.getStatus()).isEqualTo("Approved");

        ProcessInstance process = processRepository.findById(fixture.processId()).orElseThrow();
        assertThat(process.getStatus()).isEqualTo("InProgress");
    }

    @Test
    void andModeWithOneReject_stageBecomesOnRework() {
        TestFixture fixture = prepareProcessWithParticipants(DecisionMode.AND, 3);

        fixture.participants().get(0).decide("APPROVE");
        fixture.participants().get(1).decide("REJECT", "Needs changes");
        fixture.participants().get(2).decide("APPROVE");

        StageInstance stage = processRepository.findById(fixture.processId())
                .orElseThrow()
                .getStages().get(0);
        assertThat(stage.getStatus()).isEqualTo("OnRework");

        ProcessInstance process = processRepository.findById(fixture.processId()).orElseThrow();
        assertThat(process.getStatus()).isEqualTo("InProgress");
    }

    @Test
    void andModeWithOneApproveWithComments_stageBecomesApprovedWithComments() {
        TestFixture fixture = prepareProcessWithParticipants(DecisionMode.AND, 3);

        fixture.participants().get(0).decide("APPROVE");
        fixture.participants().get(1).decide("APPROVE_WITH_COMMENTS", "Minor issues");
        fixture.participants().get(2).decide("APPROVE");

        StageInstance stage = processRepository.findById(fixture.processId())
                .orElseThrow()
                .getStages().get(0);
        assertThat(stage.getStatus()).isEqualTo("ApprovedWithComments");

        ProcessInstance process = processRepository.findById(fixture.processId()).orElseThrow();
        assertThat(process.getStatus()).isEqualTo("InProgress");
    }

    @Test
    void aggregationDoesNotTriggerBeforeLastDecision() {
        TestFixture fixture = prepareProcessWithParticipants(DecisionMode.AND, 2);

        fixture.participants().get(0).decide("APPROVE");

        StageInstance stage = processRepository.findById(fixture.processId())
                .orElseThrow()
                .getStages().get(0);
        assertThat(stage.getStatus()).isEqualTo("Active");

        fixture.participants().get(1).decide("APPROVE");

        stage = processRepository.findById(fixture.processId())
                .orElseThrow()
                .getStages().get(0);
        assertThat(stage.getStatus()).isEqualTo("Approved");
    }

    @Test
    void anyApproveModeWithAllReject_stageBecomesOnRework() {
        TestFixture fixture = prepareProcessWithParticipants(DecisionMode.ANY_APPROVE, 2);

        fixture.participants().get(0).decide("REJECT", "Bad");
        fixture.participants().get(1).decide("REJECT", "Bad");

        StageInstance stage = processRepository.findById(fixture.processId())
                .orElseThrow()
                .getStages().get(0);
        assertThat(stage.getStatus()).isEqualTo("OnRework");
    }

    @Test
    void anyApproveModeWithOneApprove_stageBecomesApproved() {
        TestFixture fixture = prepareProcessWithParticipants(DecisionMode.ANY_APPROVE, 2);

        fixture.participants().get(0).decide("APPROVE");
        fixture.participants().get(1).decide("REJECT", "Not good");

        StageInstance stage = processRepository.findById(fixture.processId())
                .orElseThrow()
                .getStages().get(0);
        assertThat(stage.getStatus()).isEqualTo("Approved");
    }

    @Test
    void anyRejectModeWithAllApprove_stageBecomesApproved() {
        TestFixture fixture = prepareProcessWithParticipants(DecisionMode.ANY_REJECT, 2);

        fixture.participants().get(0).decide("APPROVE");
        fixture.participants().get(1).decide("APPROVE");

        StageInstance stage = processRepository.findById(fixture.processId())
                .orElseThrow()
                .getStages().get(0);
        assertThat(stage.getStatus()).isEqualTo("Approved");
    }

    @Test
    void anyRejectModeWithOneReject_stageBecomesOnRework() {
        TestFixture fixture = prepareProcessWithParticipants(DecisionMode.ANY_REJECT, 2);

        fixture.participants().get(0).decide("APPROVE");
        fixture.participants().get(1).decide("REJECT", "Bad");

        StageInstance stage = processRepository.findById(fixture.processId())
                .orElseThrow()
                .getStages().get(0);
        assertThat(stage.getStatus()).isEqualTo("OnRework");
    }

    @Test
    void firstRejectFailFastModeWithAllApprove_stageBecomesApproved() {
        TestFixture fixture = prepareProcessWithParticipants(DecisionMode.FIRST_REJECT_FAIL_FAST, 2);

        fixture.participants().get(0).decide("APPROVE");
        fixture.participants().get(1).decide("APPROVE");

        StageInstance stage = processRepository.findById(fixture.processId())
                .orElseThrow()
                .getStages().get(0);
        assertThat(stage.getStatus()).isEqualTo("Approved");
    }

    @Test
    void firstRejectFailFastModeWithOneReject_stageBecomesOnRework() {
        TestFixture fixture = prepareProcessWithParticipants(DecisionMode.FIRST_REJECT_FAIL_FAST, 2);

        fixture.participants().get(0).decide("REJECT", "Bad");
        fixture.participants().get(1).decide("APPROVE");

        StageInstance stage = processRepository.findById(fixture.processId())
                .orElseThrow()
                .getStages().get(0);
        assertThat(stage.getStatus()).isEqualTo("OnRework");
    }

    private record TestFixture(UUID processId, UUID stageId, List<ParticipantHelper> participants) {
    }

    private record ParticipantHelper(UUID id, UUID userId, DecisionService decisionService,
                                     UUID processId, UUID stageId) {
        void decide(String decisionType) {
            decide(decisionType, null);
        }

        void decide(String decisionType, String comment) {
            String effectiveComment = comment;
            if (effectiveComment == null && ("APPROVE_WITH_COMMENTS".equals(decisionType) || "REJECT".equals(decisionType))) {
                effectiveComment = "Auto comment for " + decisionType;
            }
            decisionService.decide(processId, stageId, id, decisionType, effectiveComment, userId);
        }
    }

    private TestFixture prepareProcessWithParticipants(DecisionMode mode, int participantCount) {
        UUID initiatorId = UUID.randomUUID();

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
                .decisionMode(mode)
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

        List<Participant> participants = new ArrayList<>();
        for (int i = 0; i < participantCount; i++) {
            Participant participant = Participant.builder()
                    .id(UUID.randomUUID())
                    .stageIteration(iteration)
                    .userId(UUID.randomUUID())
                    .role(ParticipantRole.APPROVER)
                    .orderIdx(i)
                    .status("Pending")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            participants.add(participant);
        }

        iteration.setParticipants(participants);
        stage.setIterations(List.of(iteration));
        process.setStages(List.of(stage));

        ProcessInstance savedProcess = processRepository.save(process);

        processService.startProcess(savedProcess.getId(), initiatorId);

        List<ParticipantHelper> helpers = new ArrayList<>();
        for (Participant p : participants) {
            Participant assigned = participantRepository.findById(p.getId()).orElseThrow();
            helpers.add(new ParticipantHelper(
                    assigned.getId(),
                    assigned.getUserId(),
                    decisionService,
                    savedProcess.getId(),
                    stage.getId()
            ));
        }

        return new TestFixture(savedProcess.getId(), stage.getId(), helpers);
    }
}
