package ru.coordination.approval.api.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateCommentRequest(
        UUID stageId,
        UUID remarkId,
        @NotBlank String text,
        @NotNull UUID authorId
) {
}
