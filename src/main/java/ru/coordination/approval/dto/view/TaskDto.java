package ru.coordination.approval.dto.view;

import java.time.Instant;
import java.util.UUID;

public record TaskDto(
        UUID taskId,
        UUID processId,
        UUID stageId,
        UUID participantId,
        String processEntityType,
        UUID processEntityId,
        String stageName,
        Instant dueDate,
        String status
) {
}
