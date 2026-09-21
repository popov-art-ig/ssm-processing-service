package ru.coordination.approval.dto.remark;

import java.util.UUID;

public record ResolveRemarkRequest(
        String resolutionText,
        UUID actorId
) {
}
