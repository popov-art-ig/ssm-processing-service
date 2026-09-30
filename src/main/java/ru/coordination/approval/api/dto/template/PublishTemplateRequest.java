package ru.coordination.approval.api.dto.template;

import java.util.UUID;

public record PublishTemplateRequest(
        UUID actorId
) {
}
