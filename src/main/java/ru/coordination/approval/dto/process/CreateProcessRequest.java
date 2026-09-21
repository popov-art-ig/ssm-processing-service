package ru.coordination.approval.dto.process;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public record CreateProcessRequest(
        @NotBlank String entityType,
        String entitySubtype,
        @NotNull UUID entityId,
        @NotNull Map<String, Object> attributes,
        @NotNull UUID initiatorId
) {
}
