package ru.coordination.approval.dto.remark;

import java.util.UUID;

public record RejectResolutionRequest(
        String reason,
        UUID actorId
) {
}
