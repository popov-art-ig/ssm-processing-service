package ru.coordination.approval.api.dto.comment;

import java.time.Instant;
import java.util.UUID;

public record CommentDto(
        UUID id,
        UUID processId,
        UUID stageId,
        UUID remarkId,
        String text,
        UUID authorId,
        Instant createdAt
) {
}
