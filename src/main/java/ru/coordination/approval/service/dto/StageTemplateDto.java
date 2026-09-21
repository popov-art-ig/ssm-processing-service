package ru.coordination.approval.service.dto;

import java.util.List;
import ru.coordination.approval.domain.common.DecisionMode;
import ru.coordination.approval.domain.common.ExecutionOrder;
import ru.coordination.approval.domain.common.StageType;

public record StageTemplateDto(
    Integer orderIdx,
    String name,
    String description,
    StageType stageType,
    Integer duration,
    DecisionMode decisionMode,
    ExecutionOrder executionOrder,
    Boolean isMandatory,
    Boolean isOrderMandatory,
    List<Integer> allowedReturnStages,
    List<SlotTemplateDto> actorSlots
) {}
