package ru.coordination.approval.dto.process;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProcessDto(
        UUID id,
        String entityType,
        String entitySubtype,
        UUID entityId,
        String processType,
        String status,
        UUID initiatorId,
        UUID responsibleUserId,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        List<StageDto> stages
) {
}
