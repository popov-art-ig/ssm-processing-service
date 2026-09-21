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
                .templateId(UUID.randomUUID())
                .status("Pending")
                .organizationId(UUID.randomUUID())
                .createdBy(UUID.randomUUID())
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
                .dueDate(Instant.now().plus(3, ChronoUnit.DAYS))
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

        // Add optional slot participant (or none at all)
        stage.getIterations().add(iteration);
        process.getStages().add(stage);

        assertThatCode(() -> validatorService.validate(process))
                .doesNotThrowAnyException();
    }

    @Test
    void throwsWhenNoStages() {
        ProcessInstance process = ProcessInstance.builder()
                .entityType("CONTRACT")
                .entityId(UUID.randomUUID())
                .templateId(UUID.randomUUID())
                .status("Pending")
                .organizationId(UUID.randomUUID())
                .createdBy(UUID.randomUUID())
                .createdAt(Instant.now())
                .stages(new ArrayList<>())
                .build();

        assertThatThrownBy(() -> validatorService.validate(process))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stages");
    }

    @Test
    void throwsWhenNoIterations() {
        ProcessInstance process = ProcessInstance.builder()
                .entityType("CONTRACT")
                .entityId(UUID.randomUUID())
                .templateId(UUID.randomUUID())
                .status("Pending")
                .organizationId(UUID.randomUUID())
                .createdBy(UUID.randomUUID())
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
                .templateId(UUID.randomUUID())
                .status("Pending")
                .organizationId(UUID.randomUUID())
                .createdBy(UUID.randomUUID())
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
                .dueDate(Instant.now().plus(3, ChronoUnit.DAYS))
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
