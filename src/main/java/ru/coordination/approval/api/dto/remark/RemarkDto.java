package ru.coordination.approval.api.dto.remark;

import java.time.Instant;
import java.util.UUID;

public record RemarkDto(
        UUID id,
        UUID processId,
        UUID stageId,
        String text,
        String severity,
        String status,
        UUID authorId,
        Instant createdAt,
        String resolutionText,
        Instant resolvedAt
) {
}
