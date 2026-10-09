package ru.coordination.approval.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.coordination.approval.domain.common.DecisionMode;
import ru.coordination.approval.domain.common.ExecutionOrder;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.common.StageType;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.ParticipantRole;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.domain.template.SlotTemplate;
import ru.coordination.approval.domain.template.SlotType;
import ru.coordination.approval.domain.template.StageTemplate;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.SlotTemplate;
import ru.coordination.approval.domain.template.SlotType;
import ru.coordination.approval.domain.template.StageTemplate;
import ru.coordination.approval.exception.RequiredSlotNotResolvedException;

class RouteValidatorServiceTest {

    private RouteValidatorService validatorService;

    @BeforeEach
    void setUp() {
        validatorService = new RouteValidatorService();
    }

    @Test
    void passesForValidProcess() {
        ProcessInstance process = createValidProcess();

        assertThatCode(() -> validatorService.validate(process))
                .doesNotThrowAnyException();
    }

    @Test
    void throwsWhenRequiredSlotNotResolved() {
        ProcessInstance process = createValidProcess();

        // Clear participants from first stage
        StageIteration iteration = process.getStages().get(0).getIterations().get(0);
        iteration.getParticipants().clear();

        assertThatThrownBy(() -> validatorService.validate(process))
                .isInstanceOf(RequiredSlotNotResolvedException.class)
                .hasMessageContaining("required");
    }

    @Test
    void passesWhenOptionalSlotNotResolved() {
        ProcessInstance process = ProcessInstance.builder()
                .entityType("CONTRACT")
                .entityId(UUID.randomUUID())
                .templateRef(UUID.randomUUID())
                .status("Pending")
                .createdAt(Instant.now())
                .stages(new ArrayList<>())
                .build();

        StageInstance stage = StageInstance.builder()
                .process(process)
                .orderIdx(1)
                .stageType(StageType.APPROVAL)
                .duration(3)
                .executionOrder(ExecutionOrder.PARALLEL)
                .decisionMode(DecisionMode.AND)
                .status("Pending")
                .dueAt(Instant.now().plus(3, ChronoUnit.DAYS))
                .createdAt(Instant.now())
                .iterations(new ArrayList<>())
                .build();

        StageIteration iteration = StageIteration.builder()
                .stage(stage)
                .iterationIdx(1)
                .status("Pending")
                .createdAt(Instant.now())
                .participants(new ArrayList<>())
                .build();

        // Ни одного участника, но и обязательных слотов в шаблоне нет => маршрут валиден.
        stage.getIterations().add(iteration);
        process.getStages().add(stage);

        StageTemplate optionalSlotStage = StageTemplate.builder()
                .orderIdx(1)
                .stageType(StageType.APPROVAL)
                .duration(3)
                .actorSlots(new ArrayList<>(List.of(
                        SlotTemplate.builder()
                                .slotType(SlotType.ACTOR)
                                .orderIdx(1)
                                .required(false)
                                .acceptableRoles(new ArrayList<>())
                                .build())))
                .build();

        assertThatCode(() -> validatorService.validate(process, List.of(optionalSlotStage)))
                .doesNotThrowAnyException();
    }

    @Test
    void throwsWhenRequiredSlotNotResolvedComparedToTemplate() {
        ProcessInstance process = createValidProcess();
        process.getStages().get(0).getIterations().get(0).getParticipants().clear();

        StageTemplate requiredSlotStage = StageTemplate.builder()
                .orderIdx(1)
                .stageType(StageType.APPROVAL)
                .duration(3)
                .actorSlots(new ArrayList<>(List.of(
                        SlotTemplate.builder()
                                .slotType(SlotType.ACTOR)
                                .orderIdx(1)
                                .required(true)
                                .acceptableRoles(new ArrayList<>())
                                .build())))
                .build();

        assertThatThrownBy(() -> validatorService.validate(process, List.of(requiredSlotStage)))
                .isInstanceOf(RequiredSlotNotResolvedException.class);
    }

    @Test
    void throwsWhenNoStages() {
        ProcessInstance process = ProcessInstance.builder()
                .entityType("CONTRACT")
                .entityId(UUID.randomUUID())
                .templateRef(UUID.randomUUID())
                .status("Pending")
                .createdAt(Instant.now())
                .stages(new ArrayList<>())
                .build();

        assertThatThrownBy(() -> validatorService.validate(process))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one stage");
    }

    @Test
    void throwsWhenNoIterations() {
        ProcessInstance process = ProcessInstance.builder()
                .entityType("CONTRACT")
                .entityId(UUID.randomUUID())
                .templateRef(UUID.randomUUID())
                .status("Pending")
                .createdAt(Instant.now())
                .stages(new ArrayList<>())
                .build();

        StageInstance stage = StageInstance.builder()
                .process(process)
                .orderIdx(1)
                .stageType(StageType.APPROVAL)
                .duration(3)
                .status("Pending")
                .createdAt(Instant.now())
                .iterations(new ArrayList<>())
                .build();

        process.getStages().add(stage);

        assertThatThrownBy(() -> validatorService.validate(process))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("iteration");
    }

    private ProcessInstance createValidProcess() {
        ProcessInstance process = ProcessInstance.builder()
                .entityType("CONTRACT")
                .entityId(UUID.randomUUID())
                .templateRef(UUID.randomUUID())
                .status("Pending")
                .createdAt(Instant.now())
                .stages(new ArrayList<>())
                .build();

        StageInstance stage = StageInstance.builder()
                .process(process)
                .orderIdx(1)
                .stageType(StageType.APPROVAL)
                .duration(3)
                .executionOrder(ExecutionOrder.PARALLEL)
                .decisionMode(DecisionMode.AND)
                .status("Pending")
                .dueAt(Instant.now().plus(3, ChronoUnit.DAYS))
                .createdAt(Instant.now())
                .iterations(new ArrayList<>())
                .build();

        StageIteration iteration = StageIteration.builder()
                .stage(stage)
                .iterationIdx(1)
                .status("Pending")
                .createdAt(Instant.now())
                .participants(new ArrayList<>())
                .build();

        Participant participant = Participant.builder()
                .stageIteration(iteration)
                .userId(UUID.randomUUID())
                .role(ParticipantRole.APPROVER)
                .orderIdx(1)
                .status("Assigned")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        iteration.getParticipants().add(participant);
        stage.getIterations().add(iteration);
        process.getStages().add(stage);

        return process;
    }
}
