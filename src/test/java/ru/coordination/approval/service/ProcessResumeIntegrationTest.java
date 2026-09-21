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
import ru.coordination.approval.domain.common.DecisionMode;
import ru.coordination.approval.domain.common.ExecutionOrder;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.common.StageType;
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
 * Интеграционный тест для PHASE-08: возврат на доработку и возобновление процесса.
 * Реальные миграции V1-V23. Критерии приёмки PHASE-08 (1-8).
 */
@SpringBootTest
@ActiveProfiles("test")
class ProcessResumeIntegrationTest {

    @Autowired
    private ProcessService processService;

    @Autowired
    private DecisionService decisionService;

    @Autowired
    private ProcessRepository processRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Test
    void reworkAndResumeSameStage() {
        TestFixture fixture = prepareProcessWithStages(1);
        StageParticipant sp = fixture.stages().get(0).participants().get(0);

        decisionService.decide(fixture.processId(), sp.stageId(), sp.participantId(),
                "REJECT", "Need changes", sp.userId());

        ProcessInstance process = processRepository.findById(fixture.processId()).orElseThrow();
        assertThat(process.getStatus()).isEqualTo("OnRework");
        StageInstance stage = findStage(process, 0);
        assertThat(stage.getStatus()).isEqualTo("OnRework");

        TransitionResult resumeResult = processService.resume(fixture.processId(), 0, fixture.initiatorId());

        assertThat(resumeResult.performed()).isTrue();
        process = processRepository.findById(fixture.processId()).orElseThrow();
        stage = findStage(process, 0);

        assertThat(process.getStatus()).isEqualTo("InProgress");
        assertThat(stage.getStatus()).isEqualTo("Active");
        assertThat(stage.getIterations()).hasSize(2);

        StageIteration iteration2 = stage.getIterations().stream()
                .filter(it -> it.getIterationIdx() == 1)
                .findFirst()
                .orElseThrow();

        assertThat(iteration2.getParticipants()).hasSize(1);
        Participant newParticipant = iteration2.getParticipants().get(0);
        assertThat(newParticipant.getUserId()).isEqualTo(sp.userId());
    }

    @Test
    void resumeToEarlierStageRejectsIntermediateStages() {
        TestFixture fixture = prepareProcessWithStages(3);

        StageParticipant p1 = fixture.stages().get(0).participants().get(0);
        decisionService.decide(fixture.processId(), p1.stageId(), p1.participantId(),
                "APPROVE", null, p1.userId());

        ProcessInstance process = processRepository.findById(fixture.processId()).orElseThrow();
        StageInstance stage2 = findStage(process, 1);
        assertThat(stage2.getStatus()).isEqualTo("Active");

        StageParticipant p2 = fixture.stages().get(1).participants().get(0);
        decisionService.decide(fixture.processId(), p2.stageId(), p2.participantId(),
                "APPROVE", null, p2.userId());

        process = processRepository.findById(fixture.processId()).orElseThrow();
        StageInstance stage3 = findStage(process, 2);
        assertThat(stage3.getStatus()).isEqualTo("Active");

        StageParticipant p3 = fixture.stages().get(2).participants().get(0);
        decisionService.decide(fixture.processId(), p3.stageId(), p3.participantId(),
                "REJECT", "Back to start", p3.userId());

        process = processRepository.findById(fixture.processId()).orElseThrow();
        assertThat(process.getStatus()).isEqualTo("OnRework");
        assertThat(findStage(process, 2).getStatus()).isEqualTo("OnRework");

        TransitionResult resumeResult = processService.resume(fixture.processId(), 0, fixture.initiatorId());

        assertThat(resumeResult.performed()).isTrue();
        process = processRepository.findById(fixture.processId()).orElseThrow();

        assertThat(process.getStatus()).isEqualTo("InProgress");
        assertThat(findStage(process, 0).getStatus()).isEqualTo("Active");
        assertThat(findStage(process, 1).getStatus()).isEqualTo("Rejected");
        assertThat(findStage(process, 2).getStatus()).isEqualTo("Rejected");

        assertThat(findStage(process, 0).getIterations()).hasSize(2);
    }

    @Test
    void resumeBlockedWhenProcessNotOnRework() {
        TestFixture fixture = prepareProcessWithStages(1);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> processService.resume(fixture.processId(), 0, fixture.initiatorId()));

        assertThat(exception.getMessage()).isEqualTo("Process is not OnRework");
    }

    @Test
    void resumeBlockedWhenTargetStageInvalid() {
        TestFixture fixture = prepareProcessWithStages(3);

        StageParticipant p1 = fixture.stages().get(0).participants().get(0);
        decisionService.decide(fixture.processId(), p1.stageId(), p1.participantId(),
                "REJECT", "Reject", p1.userId());

        ProcessInstance process = processRepository.findById(fixture.processId()).orElseThrow();
        assertThat(process.getStatus()).isEqualTo("OnRework");

        TransitionResult result = processService.resume(fixture.processId(), 5, fixture.initiatorId());

        assertThat(result.performed()).isFalse();
    }

    @Test
    void resumeBlockedWhenActorIsNotInitiator() {
        TestFixture fixture = prepareProcessWithStages(1);
        StageParticipant sp = fixture.stages().get(0).participants().get(0);

        decisionService.decide(fixture.processId(), sp.stageId(), sp.participantId(),
                "REJECT", "Need changes", sp.userId());

        ProcessInstance process = processRepository.findById(fixture.processId()).orElseThrow();
        assertThat(process.getStatus()).isEqualTo("OnRework");

        UUID nonInitiator = UUID.randomUUID();
        TransitionResult result = processService.resume(fixture.processId(), 0, nonInitiator);

        assertThat(result.performed()).isFalse();
    }

    private StageInstance findStage(ProcessInstance process, int orderIdx) {
        return process.getStages().stream()
                .filter(s -> s.getOrderIdx() == orderIdx)
                .findFirst()
                .orElseThrow();
    }

    private record StageParticipant(UUID stageId, UUID participantId, UUID userId) {
    }

    private record StageFixture(UUID stageId, List<StageParticipant> participants) {
    }

    private record TestFixture(UUID processId, UUID initiatorId, List<StageFixture> stages) {
    }

    private TestFixture prepareProcessWithStages(int stageCount) {
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

        List<UUID> participantUserIds = new ArrayList<>();
        for (int i = 0; i < stageCount; i++) {
            UUID userId = UUID.randomUUID();
            participantUserIds.add(userId);

            StageInstance stage = StageInstance.builder()
                    .id(UUID.randomUUID())
                    .process(process)
                    .orderIdx(i)
                    .originalOrderIdx(i)
                    .stageType(StageType.APPROVAL)
                    .duration(3)
                    .decisionMode(DecisionMode.AND)
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
            process.getStages().add(stage);
        }

        ProcessInstance savedProcess = processRepository.save(process);

        processService.startProcess(savedProcess.getId(), initiatorId);

        ProcessInstance reloaded = processRepository.findById(savedProcess.getId()).orElseThrow();
        List<StageFixture> stageFixtures = new ArrayList<>();
        for (StageInstance stage : reloaded.getStages()) {
            List<StageParticipant> participants = new ArrayList<>();
            StageIteration iter = stage.getIterations().stream()
                    .max((a, b) -> Integer.compare(a.getIterationIdx(), b.getIterationIdx()))
                    .orElseThrow();
            for (Participant p : iter.getParticipants()) {
                participants.add(new StageParticipant(stage.getId(), p.getId(), p.getUserId()));
            }
            stageFixtures.add(new StageFixture(stage.getId(), participants));
        }

        return new TestFixture(savedProcess.getId(), initiatorId, stageFixtures);
    }
}
