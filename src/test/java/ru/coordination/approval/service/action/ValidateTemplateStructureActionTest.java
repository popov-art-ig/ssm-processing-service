package ru.coordination.approval.service.action;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.DecisionMode;
import ru.coordination.approval.domain.common.ExecutionOrder;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.common.StageType;
import ru.coordination.approval.domain.template.SlotTemplate;
import ru.coordination.approval.domain.template.SlotType;
import ru.coordination.approval.domain.template.StageTemplate;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.engine.TransitionContext;

class ValidateTemplateStructureActionTest {

    private final ValidateTemplateStructureAction action = new ValidateTemplateStructureAction();

    @Test
    void passesForValidStandardTemplate() {
        Template template = standardTemplate(3, ExecutionOrder.PARALLEL, DecisionMode.AND, null);
        TransitionContext context = new TransitionContext(template, UUID.randomUUID(), ActorType.USER, Map.of());

        assertThatCode(() -> action.execute(context)).doesNotThrowAnyException();
    }

    @Test
    void throwsWhenStandardTemplateHasNoExecutionOrder() {
        Template template = standardTemplate(3, null, DecisionMode.AND, null);
        TransitionContext context = new TransitionContext(template, UUID.randomUUID(), ActorType.USER, Map.of());

        assertThatThrownBy(() -> action.execute(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("executionOrder");
    }

    @Test
    void throwsWhenStandardTemplateHasNoDecisionMode() {
        Template template = standardTemplate(3, ExecutionOrder.PARALLEL, null, null);
        TransitionContext context = new TransitionContext(template, UUID.randomUUID(), ActorType.USER, Map.of());

        assertThatThrownBy(() -> action.execute(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decisionMode");
    }

    @Test
    void throwsWhenDurationIsZero() {
        Template template = standardTemplate(0, ExecutionOrder.PARALLEL, DecisionMode.AND, null);
        TransitionContext context = new TransitionContext(template, UUID.randomUUID(), ActorType.USER, Map.of());

        assertThatThrownBy(() -> action.execute(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duration");
    }

    @Test
    void throwsWhenAllowedReturnStagesGreaterThanCurrent() {
        Template template = standardTemplate(3, ExecutionOrder.PARALLEL, DecisionMode.AND, List.of(5));
        TransitionContext context = new TransitionContext(template, UUID.randomUUID(), ActorType.USER, Map.of());

        assertThatThrownBy(() -> action.execute(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowedReturnStages");
    }

    @Test
    void throwsWhenUnifiedTemplateHasExecutionOrder() {
        Template template = Template.builder()
                .id(UUID.randomUUID())
                .name("Unified")
                .processType(ProcessType.UNIFIED)
                .status(LifecycleStatus.DRAFT)
                .version(1)
                .createdAt(Instant.now())
                .createdBy(UUID.randomUUID())
                .updatedAt(Instant.now())
                .stages(new ArrayList<>())
                .build();

        StageTemplate stage = StageTemplate.builder()
                .id(UUID.randomUUID())
                .template(template)
                .orderIdx(1)
                .name("Stage 1")
                .stageType(StageType.APPROVAL)
                .duration(3)
                .executionOrder(ExecutionOrder.PARALLEL)
                .createdAt(Instant.now())
                .actorSlots(new ArrayList<>())
                .build();
        template.getStages().add(stage);

        TransitionContext context = new TransitionContext(template, UUID.randomUUID(), ActorType.USER, Map.of());

        assertThatThrownBy(() -> action.execute(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("executionOrder");
    }

    @Test
    void throwsWhenActorSlotHasStageTemplateNull() {
        Template template = standardTemplate(3, ExecutionOrder.PARALLEL, DecisionMode.AND, null);
        StageTemplate stage = template.getStages().get(0);

        SlotTemplate invalidSlot = SlotTemplate.builder()
                .id(UUID.randomUUID())
                .stageTemplate(null)
                .slotType(SlotType.ACTOR)
                .orderIdx(1)
                .required(true)
                .createdAt(Instant.now())
                .children(new ArrayList<>())
                .build();
        stage.getActorSlots().add(invalidSlot);

        TransitionContext context = new TransitionContext(template, UUID.randomUUID(), ActorType.USER, Map.of());

        assertThatThrownBy(() -> action.execute(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACTOR");
    }

    @Test
    void throwsWhenEntityIsNotTemplate() {
        TransitionContext context = new TransitionContext("not a template", UUID.randomUUID(), ActorType.USER, Map.of());

        assertThatThrownBy(() -> action.execute(context))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Template standardTemplate(int duration, ExecutionOrder executionOrder,
                                      DecisionMode decisionMode, List<Integer> allowedReturnStages) {
        Template template = Template.builder()
                .id(UUID.randomUUID())
                .name("Standard")
                .processType(ProcessType.STANDARD)
                .status(LifecycleStatus.DRAFT)
                .version(1)
                .createdAt(Instant.now())
                .createdBy(UUID.randomUUID())
                .updatedAt(Instant.now())
                .stages(new ArrayList<>())
                .build();

        StageTemplate stage = StageTemplate.builder()
                .id(UUID.randomUUID())
                .template(template)
                .orderIdx(1)
                .name("Stage 1")
                .stageType(StageType.APPROVAL)
                .duration(duration)
                .executionOrder(executionOrder)
                .decisionMode(decisionMode)
                .allowedReturnStages(allowedReturnStages)
                .createdAt(Instant.now())
                .actorSlots(new ArrayList<>())
                .build();

        template.getStages().add(stage);
        return template;
    }
}
