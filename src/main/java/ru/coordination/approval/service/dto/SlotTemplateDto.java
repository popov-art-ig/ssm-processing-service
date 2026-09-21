package ru.coordination.approval.service.dto;

import java.util.List;
import java.util.UUID;

public record SlotTemplateDto(
    Integer orderIdx,
    UUID userId,
    UUID organizationId,
    List<UUID> acceptableRoles,
    Boolean required,
    Boolean isUserEditable,
    Boolean isOrganizationEditable,
    Boolean isDeletable
) {}
