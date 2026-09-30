package ru.coordination.approval.api.dto.remark;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateRemarkRequest(
        @NotNull UUID stageId,
        @NotBlank String text,
        @NotBlank String severity,
        @NotNull UUID authorId
) {
}
