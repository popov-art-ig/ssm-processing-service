package ru.coordination.approval.api.dto.process;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ResumeProcessRequest(
        @NotNull UUID targetStageId,
        @NotNull UUID actorId
) {
}
