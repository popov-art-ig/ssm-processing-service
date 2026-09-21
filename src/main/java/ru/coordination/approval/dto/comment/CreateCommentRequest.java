package ru.coordination.approval.dto.comment;

import java.util.UUID;

public record CreateCommentRequest(
        UUID stageId,
        UUID remarkId,
        String text,
        UUID authorId
) {
}
