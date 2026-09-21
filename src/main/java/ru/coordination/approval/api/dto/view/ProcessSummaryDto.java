package ru.coordination.approval.api.dto.view;

import java.time.Instant;
import java.util.UUID;

public record ProcessSummaryDto(
        UUID id,
        String entityType,
        UUID entityId,
        String status,
        Instant createdAt,
        Instant dueDate,
        String currentStageName
) {
}
