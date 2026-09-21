package ru.coordination.approval.service.dto;

import java.util.List;
import java.util.Map;

public record ApplicabilityRuleDto(
    Integer ruleIdx,
    List<String> entityTypes,
    List<String> entitySubtypes,
    Map<String, Object> attributeConditions
) {}
