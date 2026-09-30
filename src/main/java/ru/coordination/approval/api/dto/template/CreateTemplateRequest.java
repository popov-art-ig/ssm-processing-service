package ru.coordination.approval.api.dto.template;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateTemplateRequest(
        @NotBlank String name,
        @NotBlank String processType,
        Boolean processIterationEnabled,
        List<UUID> responsibleRoles,
        Boolean requiresResponsibleApproval,
        @NotNull List<StageTemplateDto> stages,
        List<ApplicabilityRuleDto> applicabilityRules,
        @NotNull UUID createdBy
) {
}
