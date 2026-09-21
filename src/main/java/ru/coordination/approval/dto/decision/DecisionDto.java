package ru.coordination.approval.dto.decision;

import java.time.Instant;
import java.util.UUID;

public record DecisionDto(
        UUID id,
        UUID participantId,
        String decision,
        String comment,
        Instant decidedAt,
        UUID decidedBy
) {
}
