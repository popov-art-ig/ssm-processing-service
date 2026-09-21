package ru.coordination.approval.dto.process;

import java.util.UUID;

public record ResumeProcessRequest(
        UUID targetStageId,
        UUID actorId
) {
}
