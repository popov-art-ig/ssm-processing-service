package ru.coordination.approval.api.dto.process;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StageDto(
        UUID id,
        Integer orderIdx,
        String name,
        String stageType,
        String status,
        Instant dueDate,
        Integer duration,
        String decisionMode,
        List<IterationDto> iterations
) {
}
