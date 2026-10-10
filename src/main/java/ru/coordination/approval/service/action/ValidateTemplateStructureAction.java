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

/**
 * Проверяет структуру шаблона перед публикацией (PHASE-14). Резолвится
 * {@code action_registry.handler = 'validateTemplateStructure'} (миграция V25, код реестра
 * {@code ValidateTemplateStructure}); включён в actions перехода {@code PublishTemplate}
 * ({@code DRAFT → PUBLISHED}, {@code USER_ACTION}), первым — до
 * {@link SetPublishedTimestampAction}.
 *
 * <p><b>Контракт.</b> Действие не меняет состояние: оно либо пропускает переход, либо бросает
 * {@link IllegalArgumentException} с указанием конкретного нарушения, и тогда переход (вместе с
 * записью {@code publishedAt}) откатывается транзакцией. Проверяются: непустой набор этапов;
 * {@code duration > 0} у каждого этапа; согласованность {@code executionOrder} и
 * {@code decisionMode} с {@code ProcessType} ({@code STANDARD} требует оба поля, {@code UNIFIED}
 * запрещает); вхождение каждого {@code allowedReturnStages} в диапазон «не позже самого этапа»;
 * корректность слотов ({@code ACTOR} без {@code parentSlot} и с {@code stageTemplate},
 * {@code ADDITIONAL_APPROVER} — наоборот) и запрет {@code isOrganizationEditable = true} при
 * {@code isUserEditable = false}.
 *
 * <p><b>Почему исключение, а не {@code false}-подобный отказ.</b> Контракт
 * {@link ru.coordination.approval.engine.registry.Action} не умеет отклонять переход: движок
 * считает выполнение действия успешным, если оно не бросило исключение. Поэтому единственный
 * способ заблокировать публикацию из действия — исключение, а бизнес-проверки, у которых должен
 * быть мягкий отказ, вынесены в guards (например, {@code AllMandatorySlotsValid}).
 *
 * <p><b>Кода в спецификации нет.</b> В {@code 05_guards_actions_registry.md} §5.9 ближайшее
 * служебное действие — {@code A-UTIL-002 ValidateRemarkComment} (проверка комментария), к
 * шаблонам оно не относится.
 *
 * <p><b>Ограничение.</b> Проверка носит структурный характер и не сверяет шаблон со
 * спецификацией {@code 03_domain_model.md} полностью: уникальность {@code orderIdx} этапов и
 * слотов, дубли ролей в {@code acceptableRoles} и согласованность {@code applicableRules} здесь
 * не проверяются.
 */
@Component("validateTemplateStructure")
public class ValidateTemplateStructureAction implements Action {

    /**
     * Валидирует этапы и слоты шаблона.
     *
     * @throws IllegalArgumentException если сущность перехода не {@link Template} либо структура
     *         шаблона нарушает одно из перечисленных в описании класса правил
     */
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
