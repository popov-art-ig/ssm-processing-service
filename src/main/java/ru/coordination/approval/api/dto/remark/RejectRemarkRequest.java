package ru.coordination.approval.api.dto.remark;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RejectRemarkRequest(
        @NotBlank String reason,
        @NotNull UUID actorId
) {
}
