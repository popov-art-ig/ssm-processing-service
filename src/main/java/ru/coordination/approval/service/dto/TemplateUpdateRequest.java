package ru.coordination.approval.service.dto;

import java.util.List;
import java.util.UUID;

public record TemplateUpdateRequest(
    String name,
    Boolean processIterationEnabled,
    List<UUID> responsibleRoles,
    Boolean requiresResponsibleApproval,
    List<StageTemplateDto> stages,
    List<ApplicabilityRuleDto> applicabilityRules
) {}
