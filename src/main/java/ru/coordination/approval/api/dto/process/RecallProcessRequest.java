package ru.coordination.approval.api.dto.process;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RecallProcessRequest(
        @NotNull UUID actorId
) {
}
