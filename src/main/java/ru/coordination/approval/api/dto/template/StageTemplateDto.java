package ru.coordination.approval.api.dto.template;

import java.util.List;
import java.util.UUID;

public record StageTemplateDto(
        UUID id,
        Integer orderIdx,
        String name,
        String description,
        String stageType,
        Integer duration,
        String decisionMode,
        String executionOrder,
        Boolean isMandatory,
        Boolean isOrderMandatory,
        List<Integer> allowedReturnStages,
        List<SlotTemplateDto> actorSlots
) {
}
