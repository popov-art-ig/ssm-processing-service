package ru.coordination.approval.dto;

import java.util.UUID;

public record RouteGenerationRequest(
        String entityType,
        UUID entityId,
        UUID templateId, // nullable, for auto-selection
        UUID organizationId,
        UUID initiatorId
) {
}
