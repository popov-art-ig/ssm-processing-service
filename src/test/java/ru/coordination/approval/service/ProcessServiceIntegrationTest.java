package ru.coordination.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.coordination.approval.domain.audit.AuditEvent;
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
import ru.coordination.approval.domain.process.StageRepository;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;
import ru.coordination.approval.engine.AuditEventRepository;
import ru.coordination.approval.engine.TransitionResult;
import ru.coordination.approval.engine.exception.NoApplicableTransitionException;

/**
 * Интеграционный тест {@link ProcessService} (Testcontainers) — реальные миграции
 * {@code V1}-{@code V19} (конфиг/реестры не тестовые заглушки). Критерии приёмки PHASE-03
 * (1-5) и PHASE-04 (1-3, активация первого этапа как часть {@code StartProcess}).
 */
@SpringBootTest
@ActiveProfiles("test")
class ProcessServiceIntegrationTest {

    @Autowired
    private ProcessService processService;
    @Autowired
    private ProcessRepository processRepository;
    @Autowired
    private StageRepository stageRepository;
    @Autowired
    private ParticipantRepository participantRepository;
    @Autowired
    private TemplateRepository templateRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;

    @Test
    void startsProcessAndActivatesFirstStageWhenAllGuardsPass() {
        UUID initiatorId = UUID.randomUUID();
        ProcessFixture fixture = persistProcess(initiatorId, new StageSpec(true, 3, 2, ExecutionOrderSpec.PARALLEL));

        TransitionResult result = processService.startProcess(fixture.process().getId(), initiatorId);

        assertThat(result.performed()).isTrue();
        assertThat(result.toState()).isEqualTo("InProgress");
        assertThat(result.emittedEvents()).containsExactly("approval.process.started");

        ProcessInstance persistedProcess = processRepository.findById(fixture.process().getId()).orElseThrow();
        assertThat(persistedProcess.getStatus()).isEqualTo("InProgress");
        assertThat(persistedProcess.getStartedAt()).isNotNull();

        // PHASE-04, критерий 1: AssignStageTasks (внутри StartProcess) активирует первый этап.
        StageInstance persistedStage = stageRepository.findById(fixture.firstStageId()).orElseThrow();
        assertThat(persistedStage.getStatus()).isEqualTo("Active");
        assertThat(persistedStage.getStartedAt()).isNotNull();
        assertThat(persistedStage.getDueAt())
                .isEqualTo(persistedStage.getStartedAt().plus(persistedStage.getDuration(), ChronoUnit.DAYS));

        // PHASE-04, критерий 2: executionOrder = Parallel -> все участники Assigned.
        List<Participant> participants = participantRepository.findAllById(fixture.firstStageParticipantIds());
        assertThat(participants).hasSize(2);
        assertThat(participants).allSatisfy(p -> {
            assertThat(p.getStatus()).isEqualTo("Assigned");
            assertThat(p.getAssignedAt()).isNotNull();
        });

        // PHASE-04, критерий 1: 2 AuditEvent за один вызов (PROCESS/StartProcess, STAGE/ActivateStage).
        List<AuditEvent> processEvents = auditEventRepository.findAll().stream()
                .filter(e -> e.getEntityId().equals(fixture.process().getId()))
                .toList();
        assertThat(processEvents).hasSize(1);
        assertThat(processEvents.get(0).getAction()).isEqualTo("StartProcess");

        List<AuditEvent> stageEvents = auditEventRepository.findAll().stream()
                .filter(e -> e.getEntityId().equals(fixture.firstStageId()))
                .toList();
        assertThat(stageEvents).hasSize(1);
        assertThat(stageEvents.get(0).getAction()).isEqualTo("ActivateStage");
    }

    @Test
    void assignsOnlyFirstParticipantWhenExecutionOrderIsSequential() {
        UUID initiatorId = UUID.randomUUID();
        ProcessFixture fixture = persistProcess(initiatorId, new StageSpec(true, 3, 2, ExecutionOrderSpec.SEQUENTIAL));

        processService.startProcess(fixture.process().getId(), initiatorId);

        List<Participant> participants = fixture.firstStageParticipantIds().stream()
                .map(id -> participantRepository.findById(id).orElseThrow())
                .sorted((a, b) -> Integer.compare(a.getOrderIdx(), b.getOrderIdx()))
                .toList();
        assertThat(participants.get(0).getStatus()).isEqualTo("Assigned");
        assertThat(participants.get(0).getAssignedAt()).isNotNull();
        assertThat(participants.get(1).getStatus()).isEqualTo("Pending");
        assertThat(participants.get(1).getAssignedAt()).isNull();
    }

    @Test
    void doesNotStartWhenActorIsNotInitiator() {
        UUID initiatorId = UUID.randomUUID();
        ProcessFixture fixture = persistProcess(initiatorId, new StageSpec(true, 3, 1, ExecutionOrderSpec.PARALLEL));

        TransitionResult result = processService.startProcess(fixture.process().getId(), UUID.randomUUID());

        assertThat(result.performed()).isFalse();
        assertThat(processRepository.findById(fixture.process().getId()).orElseThrow().getStatus()).isEqualTo("Draft");
    }

    @Test
    void doesNotStartWhenMandatoryStageHasNoParticipants() {
        UUID initiatorId = UUID.randomUUID();
        ProcessFixture fixture = persistProcess(initiatorId, new StageSpec(true, 3, 0, ExecutionOrderSpec.PARALLEL));

        TransitionResult result = processService.startProcess(fixture.process().getId(), initiatorId);

        assertThat(result.performed()).isFalse();
        assertThat(processRepository.findById(fixture.process().getId()).orElseThrow().getStatus()).isEqualTo("Draft");
    }

    @Test
    void startsWhenNonMandatoryStageHasNoParticipants() {
        UUID initiatorId = UUID.randomUUID();
        ProcessFixture fixture = persistProcess(initiatorId, new StageSpec(false, 3, 0, ExecutionOrderSpec.PARALLEL));

        TransitionResult result = processService.startProcess(fixture.process().getId(), initiatorId);

        assertThat(result.performed()).isTrue();
    }

    @Test
    void repeatedStartThrowsNoApplicableTransition() {
        UUID initiatorId = UUID.randomUUID();
        ProcessFixture fixture = persistProcess(initiatorId, new StageSpec(true, 3, 1, ExecutionOrderSpec.PARALLEL));
        processService.startProcess(fixture.process().getId(), initiatorId);

        assertThrows(NoApplicableTransitionException.class,
                () -> processService.startProcess(fixture.process().getId(), initiatorId));
    }

    private enum ExecutionOrderSpec {
        PARALLEL, SEQUENTIAL
    }

    private record StageSpec(boolean mandatory, int duration, int participantCount, ExecutionOrderSpec executionOrder) {
    }

    private record ProcessFixture(ProcessInstance process, UUID firstStageId, List<UUID> firstStageParticipantIds) {
    }

    private ProcessFixture persistProcess(UUID initiatorId, StageSpec... stageSpecs) {
        Template template = persistTemplate();
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

        List<StageInstance> stages = new ArrayList<>();
        UUID firstStageId = null;
        List<UUID> firstStageParticipantIds = List.of();
        int orderIdx = 0;
        for (StageSpec spec : stageSpecs) {
            StageInstance stage = StageInstance.builder()
                    .id(UUID.randomUUID())
                    .process(process)
                    .orderIdx(orderIdx)
                    .originalOrderIdx(orderIdx)
                    .stageType(StageType.APPROVAL)
                    .duration(spec.duration())
                    .mandatory(spec.mandatory())
                    .executionOrder(spec.executionOrder() == ExecutionOrderSpec.SEQUENTIAL
                            ? ExecutionOrder.SEQUENTIAL
                            : ExecutionOrder.PARALLEL)
                    // Начальный статус STAGE state machine (V19: PROCESS/STANDARD/v1, entityType=STAGE) -
                    // ActivateStage ищет переход именно из "Pending".
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
            for (int i = 0; i < spec.participantCount(); i++) {
                participants.add(Participant.builder()
                        .id(UUID.randomUUID())
                        .stageIteration(iteration)
                        .userId(UUID.randomUUID())
                        .role(ParticipantRole.APPROVER)
                        .orderIdx(i)
                        .status("Pending")
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
            }
            iteration.setParticipants(participants);
            stage.setIterations(List.of(iteration));
            stages.add(stage);

            if (orderIdx == 0) {
                firstStageId = stage.getId();
                firstStageParticipantIds = participants.stream().map(Participant::getId).toList();
            }
            orderIdx++;
        }
        process.setStages(stages);

        ProcessInstance saved = processRepository.save(process);
        return new ProcessFixture(saved, firstStageId, firstStageParticipantIds);
    }

    private Template persistTemplate() {
        Instant now = Instant.now();
        return templateRepository.save(Template.builder()
                .id(UUID.randomUUID())
                .name("Test template " + UUID.randomUUID())
                .processType(ProcessType.STANDARD)
                .status(LifecycleStatus.PUBLISHED)
                .createdAt(now)
                .createdBy(UUID.randomUUID())
                .updatedAt(now)
                .build());
    }
}
