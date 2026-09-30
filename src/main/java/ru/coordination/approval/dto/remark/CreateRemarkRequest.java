package ru.coordination.approval.dto.remark;

import java.util.UUID;

public record CreateRemarkRequest(
        UUID stageId,
        String text,
        String severity,
        UUID authorId
) {
}
