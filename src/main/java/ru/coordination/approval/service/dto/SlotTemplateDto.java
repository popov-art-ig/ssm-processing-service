package ru.coordination.approval.service.dto;

import java.util.List;
import java.util.UUID;

/**
 * Описание слота участника на входе {@link ru.coordination.approval.service.TemplateService} — из
 * него создаётся строка {@code SlotTemplate} ({@code 03_domain_model.md} §6.5).
 *
 * <p>Дублирует {@code api.dto.template.SlotTemplateDto}, но без {@code id}: слот новой версии
 * шаблона всегда получает новый идентификатор (ADR-023).
 *
 * <p>Обнуляемые {@code Boolean}-поля означают «не задано» и разворачиваются в значения по
 * умолчанию при создании сущности: {@code required} и {@code isUserEditable} — в {@code true},
 * {@code isOrganizationEditable} и {@code isDeletable} — в {@code false}.
 */
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
