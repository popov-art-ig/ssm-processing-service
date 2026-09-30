package ru.coordination.approval.dto.remark;

import java.util.UUID;

public record RejectRemarkRequest(
        String reason,
        UUID actorId
) {
}
