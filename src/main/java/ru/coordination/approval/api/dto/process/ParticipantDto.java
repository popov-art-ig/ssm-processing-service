package ru.coordination.approval.api.dto.process;

import java.time.Instant;
import java.util.UUID;

public record ParticipantDto(
        UUID id,
        Integer orderIdx,
        UUID userId,
        UUID organizationId,
        String status,
        Instant dueDate,
        Boolean hasDecision
) {
}
