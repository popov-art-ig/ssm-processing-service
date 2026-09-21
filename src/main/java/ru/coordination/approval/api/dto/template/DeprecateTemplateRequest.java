package ru.coordination.approval.api.dto.template;

import java.util.UUID;

public record DeprecateTemplateRequest(
        UUID actorId
) {
}
