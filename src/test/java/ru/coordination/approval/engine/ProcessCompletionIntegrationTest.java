package ru.coordination.approval.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.DecisionMode;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.common.ExecutionOrder;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.common.RegistryScope;
import ru.coordination.approval.domain.common.StageType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.registry.ActionRegistryEntry;
import ru.coordination.approval.domain.registry.GuardRegistryEntry;
import ru.coordination.approval.domain.registry.StatusRegistry;
import ru.coordination.approval.domain.statemachine.StateConfig;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.domain.statemachine.TransitionConfig;
import ru.coordination.approval.domain.statemachine.TriggerType;
import ru.coordination.approval.engine.model.StateConfigRepository;
import ru.coordination.approval.engine.model.StateMachineConfigRepository;
import ru.coordination.approval.engine.model.TransitionConfigRepository;
import ru.coordination.approval.engine.registry.ActionRegistryRepository;
import ru.coordination.approval.engine.registry.GuardRegistryRepository;
import ru.coordination.approval.engine.registry.StatusRegistryRepository;
import ru.coordination.approval.engine.testsupport.EngineTestTransactionalRunner;
import ru.coordination.approval.repository.ProcessInstanceRepository;

/**
 * Интеграционный тест PHASE-07: завершение последнего этапа → активация следующего
 * (если есть) → оценка завершённости процесса → установка completedAt.
 *
 * <p>Проверяет критерии приёмки:
 * <ul>
 *   <li>AC1: Завершение последнего этапа → активация следующего
 *   <li>AC2: Завершение всех этапов → процесс переходит в Approved/Rejected
 *   <li>AC3: У процесса устанавливается completedAt
 * </ul>
 */
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/test-migration")
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessCompletionIntegrationTest {

    @Autowired
    private StateMachineConfigRepository stateMachineConfigRepository;

    @Autowired
    private StateConfigRepository stateConfigRepository;

    @Autowired
    private TransitionConfigRepository transitionConfigRepository;

    @Autowired
    private GuardRegistryRepository guardRegistryRepository;

    @Autowired
    private ActionRegistryRepository actionRegistryRepository;

    @Autowired
    private StatusRegistryRepository statusRegistryRepository;

    @Autowired
    private ProcessInstanceRepository processInstanceRepository;

    @Autowired
    private EngineTestTransactionalRunner runner;

    @Autowired
    private TransitionEngine transitionEngine;

    private UUID stageConfigId;
    private UUID processConfigId;

    @BeforeAll
    void seedConfiguration() {
        Instant now = Instant.now();

        // Guards
        guardRegistryRepository.save(GuardRegistryEntry.builder()
                .code("AllStagesCompleted")
                .displayName("All stages completed")
                .description("Checks if all stages are in completed status")
                .handler("allStagesCompletedGuard")
                .scope(RegistryScope.GLOBAL)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());

        guardRegistryRepository.save(GuardRegistryEntry.builder()
                .code("HasComments")
                .displayName("Has comments")
                .description("Checks if any stage has comments")
                .handler("hasCommentsGuard")
                .scope(RegistryScope.GLOBAL)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Actions
        actionRegistryRepository.save(ActionRegistryEntry.builder()
                .code("ActivateNextStage")
                .displayName("Activate next stage")
                .description("Transitions next stage from Pending to Active")
                .handler("activateNextStageAction")
                .scope(RegistryScope.GLOBAL)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());

        actionRegistryRepository.save(ActionRegistryEntry.builder()
                .code("EvaluateProcessCompletion")
                .displayName("Evaluate process completion")
                .description("Checks if all stages completed and triggers process transition")
                .handler("evaluateProcessCompletionAction")
                .scope(RegistryScope.GLOBAL)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());

        actionRegistryRepository.save(ActionRegistryEntry.builder()
                .code("CompleteProcess")
                .displayName("Complete process")
                .description("Sets completedAt timestamp")
                .handler("completeProcessAction")
                .scope(RegistryScope.GLOBAL)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Status registries
        statusRegistryRepository.save(StatusRegistry.builder()
                .entityType(EntityType.STAGE)
                .processType(ProcessType.STANDARD)
                .status("Pending")
                .displayName("Pending")
                .lifecycleStatus(LifecycleStatus.PENDING)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());

        statusRegistryRepository.save(StatusRegistry.builder()
                .entityType(EntityType.STAGE)
                .processType(ProcessType.STANDARD)
                .status("Active")
                .displayName("Active")
                .lifecycleStatus(LifecycleStatus.IN_PROGRESS)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());

        statusRegistryRepository.save(StatusRegistry.builder()
                .entityType(EntityType.STAGE)
                .processType(ProcessType.STANDARD)
                .status("Approved")
                .displayName("Approved")
                .lifecycleStatus(LifecycleStatus.COMPLETED)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());

        statusRegistryRepository.save(StatusRegistry.builder()
                .entityType(EntityType.PROCESS)
                .processType(ProcessType.STANDARD)
                .status("InProgress")
                .displayName("In Progress")
                .lifecycleStatus(LifecycleStatus.IN_PROGRESS)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());

        statusRegistryRepository.save(StatusRegistry.builder()
                .entityType(EntityType.PROCESS)
                .processType(ProcessType.STANDARD)
                .status("Approved")
                .displayName("Approved")
                .lifecycleStatus(LifecycleStatus.COMPLETED)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());

        statusRegistryRepository.save(StatusRegistry.builder()
                .entityType(EntityType.PROCESS)
                .processType(ProcessType.STANDARD)
                .status("ApprovedWithComments")
                .displayName("Approved With Comments")
                .lifecycleStatus(LifecycleStatus.COMPLETED)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Stage state machine
        StateMachineConfig stageConfig = stateMachineConfigRepository.save(StateMachineConfig.builder()
                .entityType(EntityType.STAGE)
                .processType(ProcessType.STANDARD)
                .version(1)
                .name("Stage workflow for process completion test")
                .isActive(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
        stageConfigId = stageConfig.getId();

        StateConfig stagePending = stateConfigRepository.save(StateConfig.builder()
                .stateMachineConfig(stageConfig)
                .state("Pending")
                .createdAt(now)
                .updatedAt(now)
                .build());

        StateConfig stageActive = stateConfigRepository.save(StateConfig.builder()
                .stateMachineConfig(stageConfig)
                .state("Active")
                .createdAt(now)
                .updatedAt(now)
                .build());

        StateConfig stageApproved = stateConfigRepository.save(StateConfig.builder()
                .stateMachineConfig(stageConfig)
                .state("Approved")
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Transition: Approved → (activate next + evaluate completion)
        transitionConfigRepository.save(TransitionConfig.builder()
                .stateMachineConfig(stageConfig)
                .fromState(stageApproved)
                .toState(stageApproved)
                .transitionCode("StageCompleted")
                .priority(100)
                .triggerType(TriggerType.SYSTEM_ACTION)
                .guardCodes(new String[] {})
                .actionCodes(new String[] {"ActivateNextStage", "EvaluateProcessCompletion"})
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Process state machine
        StateMachineConfig processConfig = stateMachineConfigRepository.save(StateMachineConfig.builder()
                .entityType(EntityType.PROCESS)
                .processType(ProcessType.STANDARD)
                .version(1)
                .name("Process workflow for completion test")
                .isActive(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
        processConfigId = processConfig.getId();

        StateConfig processInProgress = stateConfigRepository.save(StateConfig.builder()
                .stateMachineConfig(processConfig)
                .state("InProgress")
                .createdAt(now)
                .updatedAt(now)
                .build());

        StateConfig processApproved = stateConfigRepository.save(StateConfig.builder()
                .stateMachineConfig(processConfig)
                .state("Approved")
                .createdAt(now)
                .updatedAt(now)
                .build());

        StateConfig processApprovedWithComments = stateConfigRepository.save(StateConfig.builder()
                .stateMachineConfig(processConfig)
                .state("ApprovedWithComments")
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Transition: InProgress → Approved (when all stages approved, no comments)
        transitionConfigRepository.save(TransitionConfig.builder()
                .stateMachineConfig(processConfig)
                .fromState(processInProgress)
                .toState(processApproved)
                .transitionCode("ProcessApproved")
                .priority(200)
                .triggerType(TriggerType.SYSTEM_ACTION)
                .guardCodes(new String[] {"AllStagesCompleted"})
                .actionCodes(new String[] {"CompleteProcess"})
                .createdAt(now)
                .updatedAt(now)
                .build());

        // Transition: InProgress → ApprovedWithComments (when all stages approved, has comments)
        transitionConfigRepository.save(TransitionConfig.builder()
                .stateMachineConfig(processConfig)
                .fromState(processInProgress)
                .toState(processApprovedWithComments)
                .transitionCode("ProcessApprovedWithComments")
                .priority(100)
                .triggerType(TriggerType.SYSTEM_ACTION)
                .guardCodes(new String[] {"AllStagesCompleted", "HasComments"})
                .actionCodes(new String[] {"CompleteProcess"})
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    @Test
    void completingLastStageActivatesNextAndCompletesProcessWhenAllDone() {
        UUID processId = runner.runInNewTransaction(() -> {
            ProcessInstance process = ProcessInstance.builder()
                    .entityType("Document")
                    .entityId(UUID.randomUUID())
                    .templateRef(UUID.randomUUID())
                    .processType(ProcessType.STANDARD)
                    .configVersion(1)
                    .status("InProgress")
                    .initiatorId(UUID.randomUUID())
                    .createdAt(Instant.now())
                    .startedAt(Instant.now())
                    .build();

            // Create 3 stages: first Active, second and third Pending
            for (int i = 1; i <= 3; i++) {
                StageInstance stage = StageInstance.builder()
                        .process(process)
                        .orderIdx(i)
                        .originalOrderIdx(i)
                        .stageType(StageType.APPROVAL)
                        .duration(5)
                        .decisionMode(DecisionMode.UNANIMOUS)
                        .executionOrder(ExecutionOrder.PARALLEL)
                        .status(i == 1 ? "Active" : "Pending")
                        .createdAt(Instant.now())
                        .build();
                process.getStages().add(stage);
            }

            return processInstanceRepository.save(process).getId();
        });

        // Complete stage 1 → should activate stage 2
        runner.runInNewTransaction(() -> {
            ProcessInstance process = processInstanceRepository.findById(processId).orElseThrow();
            StageInstance stage1 = process.getStages().get(0);
            stage1.setStatus("Approved");
            stage1.setCompletedAt(Instant.now());
            processInstanceRepository.save(process);

            transitionEngine.transition(
                    EntityType.STAGE,
                    stage1.getId(),
                    stageConfigId,
                    "Approved",
                    TriggerType.SYSTEM_ACTION,
                    new TransitionContext(stage1, UUID.randomUUID(), ActorType.SYSTEM, Map.of()),
                    entity -> {});
        });

        // Verify stage 2 is now Active, process still InProgress
        runner.runInNewTransaction(() -> {
            ProcessInstance process = processInstanceRepository.findById(processId).orElseThrow();
            assertThat(process.getStages().get(0).getStatus()).isEqualTo("Approved");
            assertThat(process.getStages().get(1).getStatus()).isEqualTo("Active");
            assertThat(process.getStages().get(2).getStatus()).isEqualTo("Pending");
            assertThat(process.getStatus()).isEqualTo("InProgress");
            assertThat(process.getCompletedAt()).isNull();
        });

        // Complete stage 2 → should activate stage 3
        runner.runInNewTransaction(() -> {
            ProcessInstance process = processInstanceRepository.findById(processId).orElseThrow();
            StageInstance stage2 = process.getStages().get(1);
            stage2.setStatus("Approved");
            stage2.setCompletedAt(Instant.now());
            processInstanceRepository.save(process);

            transitionEngine.transition(
                    EntityType.STAGE,
                    stage2.getId(),
                    stageConfigId,
                    "Approved",
                    TriggerType.SYSTEM_ACTION,
                    new TransitionContext(stage2, UUID.randomUUID(), ActorType.SYSTEM, Map.of()),
                    entity -> {});
        });

        // Verify stage 3 is now Active
        runner.runInNewTransaction(() -> {
            ProcessInstance process = processInstanceRepository.findById(processId).orElseThrow();
            assertThat(process.getStages().get(2).getStatus()).isEqualTo("Active");
            assertThat(process.getStatus()).isEqualTo("InProgress");
        });

        // Complete stage 3 → no more stages, process should complete
        runner.runInNewTransaction(() -> {
            ProcessInstance process = processInstanceRepository.findById(processId).orElseThrow();
            StageInstance stage3 = process.getStages().get(2);
            stage3.setStatus("Approved");
            stage3.setCompletedAt(Instant.now());
            processInstanceRepository.save(process);

            transitionEngine.transition(
                    EntityType.STAGE,
                    stage3.getId(),
                    stageConfigId,
                    "Approved",
                    TriggerType.SYSTEM_ACTION,
                    new TransitionContext(stage3, UUID.randomUUID(), ActorType.SYSTEM, Map.of()),
                    entity -> {});
        });

        // Verify process is Approved and has completedAt
        runner.runInNewTransaction(() -> {
            ProcessInstance process = processInstanceRepository.findById(processId).orElseThrow();
            assertThat(process.getStatus()).isEqualTo("Approved");
            assertThat(process.getCompletedAt()).isNotNull();
        });
    }
}
