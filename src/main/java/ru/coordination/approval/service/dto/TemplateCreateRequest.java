package ru.coordination.approval.service.dto;

import java.util.List;
import java.util.UUID;
import ru.coordination.approval.domain.common.ProcessType;

public record TemplateCreateRequest(
    String name,
    ProcessType processType,
    Boolean processIterationEnabled,
    List<UUID> responsibleRoles,
    Boolean requiresResponsibleApproval,
    List<StageTemplateDto> stages,
    List<ApplicabilityRuleDto> applicabilityRules,
    UUID createdBy
) {}
