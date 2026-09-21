package ru.coordination.approval.api.dto.template;

import java.util.UUID;

public record ArchiveTemplateRequest(
        UUID actorId
) {
}
