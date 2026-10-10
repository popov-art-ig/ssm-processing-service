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
import ru.coordination.approval.domain.process.ParticipantRole;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.ProcessRepository;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.domain.process.StageRepository;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;
import ru.coordination.approval.engine.TransitionResult;

/**
 * Интеграционный тест для PHASE-08: возврат на доработку и возобновление процесса.
 * Реальные миграции V1-V23. Критерии приёмки PHASE-08 (1-8).
 *
 * <p><b>Про чтение состояния.</b> Тест намеренно НЕ аннотирован {@code @Transactional}:
 * общий с сервисом persistence context приводил к тому, что проверки видели устаревшие
 * экземпляры сущностей (статус этапа/процесса до перехода). Вместо ленивой навигации
 * {@code process.getStages()} используются запросы {@link StageRepository} — они читают
 * актуальное состояние.
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
    private StageRepository stageRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Test
    void reworkAndResumeSameStage() {
        TestFixture fixture = prepareProcessWithStages(1);
        StageParticipant sp = fixture.stages().get(0).participants().get(0);

        decisionService.decide(fixture.processId(), sp.stageId(), sp.participantId(),
                "REJECT", "Need changes", sp.userId());

        assertThat(processStatus(fixture.processId())).isEqualTo("OnRework");
        assertThat(stageStatus(fixture.processId(), 0)).isEqualTo("OnRework");

        TransitionResult resumeResult = processService.resume(fixture.processId(), 0, fixture.initiatorId());

        assertThat(resumeResult.performed()).isTrue();
        assertThat(processStatus(fixture.processId())).isEqualTo("InProgress");
        assertThat(stageStatus(fixture.processId(), 0)).isEqualTo("Active");
        assertThat(stageRepository.countIterations(sp.stageId())).isEqualTo(2);
    }

    @Test
    void resumeToEarlierStageRejectsIntermediateStages() {
        TestFixture fixture = prepareProcessWithStages(3);

        StageParticipant p1 = fixture.stages().get(0).participants().get(0);
        decisionService.decide(fixture.processId(), p1.stageId(), p1.participantId(),
                "APPROVE", null, p1.userId());

        assertThat(stageStatus(fixture.processId(), 1)).isEqualTo("Active");

        StageParticipant p2 = fixture.stages().get(1).participants().get(0);
        decisionService.decide(fixture.processId(), p2.stageId(), p2.participantId(),
                "APPROVE", null, p2.userId());

        assertThat(stageStatus(fixture.processId(), 2)).isEqualTo("Active");

        StageParticipant p3 = fixture.stages().get(2).participants().get(0);
        decisionService.decide(fixture.processId(), p3.stageId(), p3.participantId(),
                "REJECT", "Back to start", p3.userId());

        assertThat(processStatus(fixture.processId())).isEqualTo("OnRework");
        assertThat(stageStatus(fixture.processId(), 2)).isEqualTo("OnRework");

        TransitionResult resumeResult = processService.resume(fixture.processId(), 0, fixture.initiatorId());

        assertThat(resumeResult.performed()).isTrue();
        assertThat(processStatus(fixture.processId())).isEqualTo("InProgress");
        assertThat(stageStatus(fixture.processId(), 0)).isEqualTo("Active");
        assertThat(stageStatus(fixture.processId(), 1)).isEqualTo("Rejected");
        assertThat(stageStatus(fixture.processId(), 2)).isEqualTo("Rejected");
        assertThat(stageRepository.countIterations(p1.stageId())).isEqualTo(2);
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

        assertThat(processStatus(fixture.processId())).isEqualTo("OnRework");

        TransitionResult result = processService.resume(fixture.processId(), 5, fixture.initiatorId());

        assertThat(result.performed()).isFalse();
    }

    @Test
    void resumeBlockedWhenActorIsNotInitiator() {
        TestFixture fixture = prepareProcessWithStages(1);
        StageParticipant sp = fixture.stages().get(0).participants().get(0);

        decisionService.decide(fixture.processId(), sp.stageId(), sp.participantId(),
                "REJECT", "Need changes", sp.userId());

        assertThat(processStatus(fixture.processId())).isEqualTo("OnRework");

        UUID nonInitiator = UUID.randomUUID();
        TransitionResult result = processService.resume(fixture.processId(), 0, nonInitiator);

        assertThat(result.performed()).isFalse();
    }

    // ------------------------------------------------------------------ вспомогательные чтения

    private String processStatus(UUID processId) {
        return processRepository.findById(processId).orElseThrow().getStatus();
    }

    private String stageStatus(UUID processId, int orderIdx) {
        return stageRepository.findByProcessIdOrderByOrderIdx(processId).stream()
                .filter(s -> s.getOrderIdx() == orderIdx)
                .findFirst()
                .orElseThrow()
                .getStatus();
    }

    // ------------------------------------------------------------------ фикстура

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

        List<StageFixture> stageFixtures = new ArrayList<>();
        for (int i = 0; i < stageCount; i++) {
            UUID userId = UUID.randomUUID();
            UUID stageId = UUID.randomUUID();
            UUID participantId = UUID.randomUUID();

            StageInstance stage = StageInstance.builder()
                    .id(stageId)
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
                    .id(participantId)
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

            stageFixtures.add(new StageFixture(stageId, List.of(new StageParticipant(stageId, participantId, userId))));
        }

        ProcessInstance savedProcess = processRepository.save(process);

        processService.startProcess(savedProcess.getId(), initiatorId);

        // ID этапов и участников зафиксированы при построении — повторное чтение через ленивую
        // навигацию не требуется.
        return new TestFixture(savedProcess.getId(), initiatorId, stageFixtures);
    }
}
