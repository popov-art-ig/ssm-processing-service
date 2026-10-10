package ru.coordination.approval.api.dto.process;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RecallProcessRequest(
        @NotNull UUID actorId,
        /** Необязательная причина отзыва; попадает в {@code audit_event.payload} перехода. */
        String reason
) {
}
