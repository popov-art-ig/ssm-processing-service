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

@Component
public class TemplateMapper {

    public TemplateDto toDto(Template template) {
        return new TemplateDto(
                template.getId(),
                template.getName(),
                template.getProcessType().name(),
                template.getStatus().name(),
                template.getVersion(),
                template.getParentTemplateId(),
                template.getProcessIterationEnabled(),
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
                stage.getStageType().name(),
                stage.getDuration(),
                stage.getDecisionMode() != null ? stage.getDecisionMode().name() : null,
                stage.getExecutionOrder() != null ? stage.getExecutionOrder().name() : null,
                stage.getIsMandatory(),
                stage.getIsOrderMandatory(),
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
                slot.getRequired(),
                slot.getIsUserEditable(),
                slot.getIsOrganizationEditable(),
                slot.getIsDeletable()
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
