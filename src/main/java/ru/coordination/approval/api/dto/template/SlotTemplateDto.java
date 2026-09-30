package ru.coordination.approval.api.dto.template;

import java.util.List;
import java.util.UUID;

public record SlotTemplateDto(
        UUID id,
        Integer orderIdx,
        UUID userId,
        UUID organizationId,
        List<UUID> acceptableRoles,
        Boolean required,
        Boolean isUserEditable,
        Boolean isOrganizationEditable,
        Boolean isDeletable
) {
}
