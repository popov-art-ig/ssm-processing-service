package ru.coordination.approval.api.dto.template;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TemplateDto(
        UUID id,
        String name,
        String processType,
        String status,
        Integer version,
        UUID parentTemplateId,
        Boolean processIterationEnabled,
        Instant publishedAt,
        List<StageTemplateDto> stages,
        List<ApplicabilityRuleDto> applicabilityRules
) {
}
