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
import ru.coordination.approval.domain.audit.AuditEvent;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.common.StageType;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.ParticipantRole;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.ProcessRepository;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;
import ru.coordination.approval.engine.AuditEventRepository;
import ru.coordination.approval.engine.TransitionResult;
import ru.coordination.approval.engine.exception.NoApplicableTransitionException;

/**
 * Интеграционный тест {@link ProcessService} (Testcontainers, PHASE-03 T9) — реальные
 * миграции {@code V1}-{@code V18} (конфиг/реестры из {@code V18}, не тестовые заглушки).
 * Критерии приёмки 1-5 тикета.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProcessServiceIntegrationTest {

    @Autowired
    private ProcessService processService;
    @Autowired
    private ProcessRepository processRepository;
    @Autowired
    private TemplateRepository templateRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;

    @Test
    void startsProcessWhenAllGuardsPass() {
        UUID initiatorId = UUID.randomUUID();
        ProcessInstance process = persistProcess(initiatorId, new StageSpec(true, 3, 1));

        TransitionResult result = processService.startProcess(process.getId(), initiatorId);

        assertThat(result.performed()).isTrue();
        assertThat(result.toState()).isEqualTo("InProgress");
        assertThat(result.emittedEvents()).containsExactly("approval.process.started");

        ProcessInstance persisted = processRepository.findById(process.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo("InProgress");
        assertThat(persisted.getStartedAt()).isNotNull();

        List<AuditEvent> events = auditEventRepository.findAll().stream()
                .filter(e -> e.getEntityId().equals(process.getId()))
                .toList();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getAction()).isEqualTo("StartProcess");
    }

    @Test
    void doesNotStartWhenActorIsNotInitiator() {
        UUID initiatorId = UUID.randomUUID();
        ProcessInstance process = persistProcess(initiatorId, new StageSpec(true, 3, 1));

        TransitionResult result = processService.startProcess(process.getId(), UUID.randomUUID());

        assertThat(result.performed()).isFalse();
        assertThat(processRepository.findById(process.getId()).orElseThrow().getStatus()).isEqualTo("Draft");
    }

    @Test
    void doesNotStartWhenMandatoryStageHasNoParticipants() {
        UUID initiatorId = UUID.randomUUID();
        ProcessInstance process = persistProcess(initiatorId, new StageSpec(true, 3, 0));

        TransitionResult result = processService.startProcess(process.getId(), initiatorId);

        assertThat(result.performed()).isFalse();
        assertThat(processRepository.findById(process.getId()).orElseThrow().getStatus()).isEqualTo("Draft");
    }

    @Test
    void startsWhenNonMandatoryStageHasNoParticipants() {
        UUID initiatorId = UUID.randomUUID();
        ProcessInstance process = persistProcess(initiatorId, new StageSpec(false, 3, 0));

        TransitionResult result = processService.startProcess(process.getId(), initiatorId);

        assertThat(result.performed()).isTrue();
    }

    @Test
    void repeatedStartThrowsNoApplicableTransition() {
        UUID initiatorId = UUID.randomUUID();
        ProcessInstance process = persistProcess(initiatorId, new StageSpec(true, 3, 1));
        processService.startProcess(process.getId(), initiatorId);

        assertThrows(NoApplicableTransitionException.class,
                () -> processService.startProcess(process.getId(), initiatorId));
    }

    private record StageSpec(boolean mandatory, int duration, int participantCount) {
    }

    private ProcessInstance persistProcess(UUID initiatorId, StageSpec... stageSpecs) {
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
                    .status("ACTIVE")
                    .createdAt(now)
                    .build();
            orderIdx++;

            StageIteration iteration = StageIteration.builder()
                    .id(UUID.randomUUID())
                    .stage(stage)
                    .iterationIdx(0)
                    .status("ACTIVE")
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
                        .status("PENDING")
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
            }
            iteration.setParticipants(participants);
            stage.setIterations(List.of(iteration));
            stages.add(stage);
        }
        process.setStages(stages);

        return processRepository.save(process);
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
