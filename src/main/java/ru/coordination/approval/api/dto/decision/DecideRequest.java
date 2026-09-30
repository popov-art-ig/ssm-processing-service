package ru.coordination.approval.api.dto.decision;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record DecideRequest(
        @NotBlank String decision,
        String comment,
        @NotNull UUID actorId
) {
}
