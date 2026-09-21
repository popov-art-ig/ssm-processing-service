package ru.coordination.approval.service.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.common.StageType;
import ru.coordination.approval.domain.template.SlotTemplate;
import ru.coordination.approval.domain.template.SlotType;
import ru.coordination.approval.domain.template.StageTemplate;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.engine.TransitionContext;

class AllMandatorySlotsValidGuardTest {

    private final AllMandatorySlotsValidGuard guard = new AllMandatorySlotsValidGuard();

    @Test
    void returnsFalseWhenMandatorySlotHasNoUserAndNoRoles() {
        Template template = templateWithSlot(true, null, new ArrayList<>());
        TransitionContext context = new TransitionContext(template, UUID.randomUUID(), ActorType.USER, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsTrueWhenMandatorySlotHasUserId() {
        Template template = templateWithSlot(true, UUID.randomUUID(), new ArrayList<>());
        TransitionContext context = new TransitionContext(template, UUID.randomUUID(), ActorType.USER, Map.of());

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsTrueWhenMandatorySlotHasAcceptableRoles() {
        Template template = templateWithSlot(true, null, List.of(UUID.randomUUID()));
        TransitionContext context = new TransitionContext(template, UUID.randomUUID(), ActorType.USER, Map.of());

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsTrueWhenOptionalSlotHasNoUserAndNoRoles() {
        Template template = templateWithSlot(false, null, new ArrayList<>());
        TransitionContext context = new TransitionContext(template, UUID.randomUUID(), ActorType.USER, Map.of());

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsFalseWhenTemplateHasNoStages() {
        Template template = Template.builder()
                .id(UUID.randomUUID())
                .name("Empty")
                .processType(ProcessType.STANDARD)
                .status(LifecycleStatus.DRAFT)
                .version(1)
                .createdAt(Instant.now())
                .createdBy(UUID.randomUUID())
                .updatedAt(Instant.now())
                .stages(new ArrayList<>())
                .build();
        TransitionContext context = new TransitionContext(template, UUID.randomUUID(), ActorType.USER, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseWhenEntityIsNotTemplate() {
        TransitionContext context = new TransitionContext("not a template", UUID.randomUUID(), ActorType.USER, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    private Template templateWithSlot(boolean required, UUID userId, List<UUID> acceptableRoles) {
        Template template = Template.builder()
                .id(UUID.randomUUID())
                .name("T")
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
                .duration(3)
                .createdAt(Instant.now())
                .actorSlots(new ArrayList<>())
                .build();

        SlotTemplate slot = SlotTemplate.builder()
                .id(UUID.randomUUID())
                .stageTemplate(stage)
                .slotType(SlotType.ACTOR)
                .orderIdx(1)
                .userId(userId)
                .acceptableRoles(acceptableRoles)
                .required(required)
                .createdAt(Instant.now())
                .children(new ArrayList<>())
                .build();

        stage.getActorSlots().add(slot);
        template.getStages().add(stage);
        return template;
    }
}
