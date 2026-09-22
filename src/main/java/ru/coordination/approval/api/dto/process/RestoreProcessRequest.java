package ru.coordination.approval.api.dto.process;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RestoreProcessRequest(
        @NotNull UUID actorId
) {
}
