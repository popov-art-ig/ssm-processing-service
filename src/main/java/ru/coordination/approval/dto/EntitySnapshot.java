package ru.coordination.approval.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EntitySnapshot(
        String entityType,
        String entitySubtype, // nullable
        UUID entityId,
        Map<String, Object> attributes,
        Instant fetchedAt
) {
}
