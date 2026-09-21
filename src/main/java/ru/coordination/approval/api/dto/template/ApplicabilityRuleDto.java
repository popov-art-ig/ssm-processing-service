package ru.coordination.approval.api.dto.template;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ApplicabilityRuleDto(
        UUID id,
        Integer ruleIdx,
        List<String> entityTypes,
        List<String> entitySubtypes,
        Map<String, Object> attributeConditions
) {
}
