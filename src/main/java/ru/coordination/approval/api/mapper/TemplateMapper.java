package ru.coordination.approval.api.mapper;

import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import ru.coordination.approval.api.dto.template.ApplicabilityRuleDto;
import ru.coordination.approval.api.dto.template.SlotTemplateDto;
import ru.coordination.approval.api.dto.template.StageTemplateDto;
import ru.coordination.approval.api.dto.template.TemplateDto;
import ru.coordination.approval.domain.template.ApplicabilityRule;
import ru.coordination.approval.domain.template.SlotTemplate;
import ru.coordination.approval.domain.template.StageTemplate;
import ru.coordination.approval.domain.template.Template;

/**
 * Преобразование шаблона в API-представление: {@code Template} → {@link TemplateDto},
 * {@code StageTemplate} → {@link StageTemplateDto}, {@code SlotTemplate} → {@link SlotTemplateDto},
 * {@code ApplicabilityRule} → {@link ApplicabilityRuleDto}.
 *
 * <p>Маппер обходит ленивые коллекции ({@code stages → actorSlots}, {@code applicabilityRules}),
 * поэтому все вызовы идут внутри транзакции: контроллер помечает чтение шаблонов
 * {@code readOnly = true}.
 *
 * <p>Что теряется: у слота не выводятся {@code slotType}, {@code parentSlot} и {@code dueOffset} —
 * значит, вложенные дополнительные согласующие через API не видны; {@code enum}-поля передаются
 * строками и защищены от {@code null}.
 */
@Component
public class TemplateMapper {

    /**
     * Представление шаблона вместе с этапами, слотами этапов и правилами применимости. Обходит
     * ленивые коллекции, поэтому вызывается внутри транзакции чтения.
     */
    public TemplateDto toDto(Template template) {
        return new TemplateDto(
                template.getId(),
                template.getName(),
                template.getProcessType() == null ? null : template.getProcessType().name(),
                template.getStatus() == null ? null : template.getStatus().name(),
                template.getVersion(),
                template.getParentTemplateId(),
                template.isProcessIterationEnabled(),
                template.getPublishedAt(),
                template.getStages().stream()
                        .map(this::toStageTemplateDto)
                        .toList(),
                template.getApplicabilityRules().stream()
                        .map(this::toApplicabilityRuleDto)
                        .toList()
        );
    }

    private StageTemplateDto toStageTemplateDto(StageTemplate stage) {
        return new StageTemplateDto(
                stage.getId(),
                stage.getOrderIdx(),
                stage.getName(),
                stage.getDescription(),
                stage.getStageType() == null ? null : stage.getStageType().name(),
                stage.getDuration(),
                stage.getDecisionMode() != null ? stage.getDecisionMode().name() : null,
                stage.getExecutionOrder() != null ? stage.getExecutionOrder().name() : null,
                stage.isMandatory(),
                stage.isOrderMandatory(),
                stage.getAllowedReturnStages(),
                stage.getActorSlots().stream()
                        .map(this::toSlotTemplateDto)
                        .toList()
        );
    }

    private SlotTemplateDto toSlotTemplateDto(SlotTemplate slot) {
        return new SlotTemplateDto(
                slot.getId(),
                slot.getOrderIdx(),
                slot.getUserId(),
                slot.getOrganizationId(),
                slot.getAcceptableRoles(),
                slot.isRequired(),
                slot.isUserEditable(),
                slot.isOrganizationEditable(),
                slot.isDeletable()
        );
    }

    private ApplicabilityRuleDto toApplicabilityRuleDto(ApplicabilityRule rule) {
        return new ApplicabilityRuleDto(
                rule.getId(),
                rule.getRuleIdx(),
                rule.getEntityTypes(),
                rule.getEntitySubtypes(),
                rule.getAttributeConditions()
        );
    }
}
