package ru.coordination.approval.service.action;

import java.util.List;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.template.SlotTemplate;
import ru.coordination.approval.domain.template.SlotType;
import ru.coordination.approval.domain.template.StageTemplate;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

@Component("validateTemplateStructure")
public class ValidateTemplateStructureAction implements Action {

    @Override
    public void execute(TransitionContext context) {
        if (!(context.entity() instanceof Template template)) {
            throw new IllegalArgumentException("Entity is not a Template");
        }

        List<StageTemplate> stages = template.getStages();
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("Template must have at least one stage");
        }

        for (StageTemplate stage : stages) {
            validateStage(template, stage);
        }
    }

    private void validateStage(Template template, StageTemplate stage) {
        if (stage.getDuration() == null || stage.getDuration() <= 0) {
            throw new IllegalArgumentException(
                    "Stage '" + stage.getName() + "' must have duration > 0");
        }

        if (template.getProcessType() == ProcessType.STANDARD) {
            if (stage.getExecutionOrder() == null) {
                throw new IllegalArgumentException(
                        "Stage '" + stage.getName() + "' must have executionOrder for STANDARD process");
            }
            if (stage.getDecisionMode() == null) {
                throw new IllegalArgumentException(
                        "Stage '" + stage.getName() + "' must have decisionMode for STANDARD process");
            }
        } else if (template.getProcessType() == ProcessType.UNIFIED) {
            if (stage.getExecutionOrder() != null) {
                throw new IllegalArgumentException(
                        "Stage '" + stage.getName() + "' must not have executionOrder for UNIFIED process");
            }
            if (stage.getDecisionMode() != null) {
                throw new IllegalArgumentException(
                        "Stage '" + stage.getName() + "' must not have decisionMode for UNIFIED process");
            }
        }

        List<Integer> allowedReturnStages = stage.getAllowedReturnStages();
        if (allowedReturnStages != null && !allowedReturnStages.isEmpty()) {
            for (Integer returnStageIdx : allowedReturnStages) {
                if (returnStageIdx > stage.getOrderIdx()) {
                    throw new IllegalArgumentException(
                            "Stage '" + stage.getName() + "' allowedReturnStages contains invalid orderIdx " +
                            returnStageIdx + " which is greater than current stage orderIdx " + stage.getOrderIdx());
                }
            }
        }

        validateSlots(stage);
    }

    private void validateSlots(StageTemplate stage) {
        List<SlotTemplate> slots = stage.getActorSlots();
        if (slots == null) {
            return;
        }

        for (SlotTemplate slot : slots) {
            if (slot.getSlotType() == SlotType.ACTOR) {
                if (slot.getParentSlot() != null) {
                    throw new IllegalArgumentException(
                            "Slot with slotType=ACTOR must have parentSlot=null");
                }
                if (slot.getStageTemplate() == null) {
                    throw new IllegalArgumentException(
                            "Slot with slotType=ACTOR must have stageTemplate set");
                }
            } else if (slot.getSlotType() == SlotType.ADDITIONAL_APPROVER) {
                if (slot.getParentSlot() == null) {
                    throw new IllegalArgumentException(
                            "Slot with slotType=ADDITIONAL_APPROVER must have parentSlot set");
                }
                if (slot.getStageTemplate() != null) {
                    throw new IllegalArgumentException(
                            "Slot with slotType=ADDITIONAL_APPROVER must have stageTemplate=null");
                }
            }

            if (slot.isOrganizationEditable() && !slot.isUserEditable()) {
                throw new IllegalArgumentException(
                        "Slot cannot have isOrganizationEditable=true and isUserEditable=false");
            }
        }
    }
}
