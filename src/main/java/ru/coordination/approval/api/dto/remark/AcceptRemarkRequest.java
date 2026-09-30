package ru.coordination.approval.api.dto.remark;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AcceptRemarkRequest(
        @NotNull UUID actorId
) {
}
