package ru.coordination.approval.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
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
import ru.coordination.approval.service.DecisionService;
import ru.coordination.approval.service.ProcessService;

/**
 * Интеграционный тест PHASE-07: завершение последнего этапа → завершение процесса.
 *
 * <p>Проверяет критерии приёмки:
 * <ul>
 *   <li>AC1: Завершение не последнего этапа активирует следующий;</li>
 *   <li>AC2: Завершение всех этапов переводит процесс в Approved;</li>
 *   <li>AC3: У процесса устанавливается completedAt.</li>
 * </ul>
 *
 * <p><b>Переписан при сборке.</b> Исходная версия этого теста не компилировалась: она собирала
 * конфигурацию state machine через несуществующий API ({@code StatusRegistry.lifecycleStatus},
 * {@code StateConfig.state}, {@code TransitionConfig.transitionCode/guardCodes/actionCodes},
 * {@code StateMachineConfig.name/isActive}), которого в доменной модели нет. Вместо ручного
 * сидирования используется то, что уже сеют миграции {@code V18}–{@code V22}, а сценарий
 * проводится через публичные {@link ProcessService} и {@link DecisionService} — тот же приём,
 * что в {@code ProcessServiceIntegrationTest} и {@code StageAggregationIntegrationTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProcessCompletionIntegrationTest {

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
    void completingLastStageCompletesProcessAndSetsCompletedAt() {
        UUID initiatorId = UUID.randomUUID();
        Fixture fixture = prepareTwoStageProcess(initiatorId);

        // Запуск: Draft -> InProgress, первый этап активируется, участники получают задачи
        processService.startProcess(fixture.processId(), initiatorId);

        assertThat(stageStatus(fixture.processId(), 0)).isEqualTo("Active");
        assertThat(stageStatus(fixture.processId(), 1)).isEqualTo("Pending");

        // Этап 1 завершён => процесс ещё InProgress, активируется этап 2 (AC1)
        fixture.decide(0, "APPROVE", null);

        assertThat(stageStatus(fixture.processId(), 0)).isEqualTo("Approved");
        assertThat(stageStatus(fixture.processId(), 1)).isEqualTo("Active");

        ProcessInstance afterFirstStage = processRepository.findById(fixture.processId()).orElseThrow();
        assertThat(afterFirstStage.getStatus()).isEqualTo("InProgress");
        assertThat(afterFirstStage.getCompletedAt()).isNull();

        // Этап 2 завершён => все этапы согласованы => процесс Approved + completedAt (AC2, AC3)
        fixture.decide(1, "APPROVE", null);

        assertThat(stageStatus(fixture.processId(), 1)).isEqualTo("Approved");

        ProcessInstance completed = processRepository.findById(fixture.processId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo("Approved");
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    void processStaysInProgressWhileNotAllStagesApproved() {
        UUID initiatorId = UUID.randomUUID();
        Fixture fixture = prepareTwoStageProcess(initiatorId);

        processService.startProcess(fixture.processId(), initiatorId);
        fixture.decide(0, "APPROVE", null);

        ProcessInstance process = processRepository.findById(fixture.processId()).orElseThrow();
        assertThat(process.getStatus()).isEqualTo("InProgress");
        assertThat(process.getCompletedAt()).isNull();
    }

    // ------------------------------------------------------------------ фикстура

    private record Fixture(UUID processId, List<UUID> stageIds, List<List<UUID>> participantIds,
                           List<UUID> participantUserIds, DecisionService decisionService) {

        void decide(int stageIndex, String decisionType, String comment) {
            UUID stageId = stageIds.get(stageIndex);
            UUID participantId = participantIds.get(stageIndex).get(0);
            UUID actorId = participantUserIds.get(stageIndex);
            decisionService.decide(processId, stageId, participantId, decisionType, comment, actorId);
        }
    }

    /** Читает статус этапа из БД, а не из памяти — состояние меняют переходы в других транзакциях. */
    private String stageStatus(UUID processId, int stageIndex) {
        return processRepository.findById(processId).orElseThrow()
                .getStages().get(stageIndex).getStatus();
    }

    /**
     * Два обязательных этапа по одному участнику ({@code DecisionMode.AND}). Возвращает
     * идентификаторы, чтобы тест читал состояние из БД, а не из памяти.
     */
    private Fixture prepareTwoStageProcess(UUID initiatorId) {
        Instant now = Instant.now();

        Template template = templateRepository.save(Template.builder()
                .id(UUID.randomUUID())
                .name("Completion template " + UUID.randomUUID())
                .processType(ProcessType.STANDARD)
                .status(LifecycleStatus.PUBLISHED)
                .version(1)
                .createdAt(now)
                .createdBy(UUID.randomUUID())
                .updatedAt(now)
                .build());

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

        List<UUID> stageIds = new ArrayList<>();
        List<List<UUID>> participantIds = new ArrayList<>();
        List<UUID> participantUserIds = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
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

            UUID approverId = UUID.randomUUID();
            Participant participant = Participant.builder()
                    .id(UUID.randomUUID())
                    .stageIteration(iteration)
                    .userId(approverId)
                    .role(ParticipantRole.APPROVER)
                    .orderIdx(0)
                    .status("Pending")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            iteration.setParticipants(List.of(participant));
            stage.setIterations(List.of(iteration));
            process.getStages().add(stage);

            stageIds.add(stage.getId());
            participantIds.add(List.of(participant.getId()));
            participantUserIds.add(approverId);
        }

        processRepository.save(process);

        // Участники перечитываются после старта процесса: AssignParticipantTasks меняет их статус
        return new Fixture(process.getId(), stageIds, participantIds, participantUserIds, decisionService);
    }
}
