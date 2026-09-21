package ru.coordination.approval.api.dto.template;

import java.util.List;
import java.util.UUID;

public record UpdateTemplateRequest(
        String name,
        Boolean processIterationEnabled,
        List<UUID> responsibleRoles,
        Boolean requiresResponsibleApproval,
        List<StageTemplateDto> stages,
        List<ApplicabilityRuleDto> applicabilityRules
) {
}
