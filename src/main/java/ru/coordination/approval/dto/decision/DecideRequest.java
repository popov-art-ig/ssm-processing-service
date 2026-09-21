package ru.coordination.approval.dto.decision;

import java.util.UUID;

public record DecideRequest(
        String decision,
        String comment,
        UUID actorId
) {
}
