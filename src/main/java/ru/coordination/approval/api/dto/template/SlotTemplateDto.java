package ru.coordination.approval.api.dto.template;

import java.util.List;
import java.util.UUID;

/**
 * Слот участника этапа шаблона ({@code 07_api_contract.md} §10.3) — место под конкретного
 * пользователя или организацию, которое заполняется при создании процесса.
 *
 * <p>Отличие от {@code SlotTemplate}: не отдаются {@code slotType}, {@code parentSlot} и
 * {@code dueOffset}, поэтому вложенность доп. согласующих через API не видна.
 *
 * @param required               обязателен ли слот к заполнению: незаполненный обязательный слот
 *                               блокирует публикацию шаблона (action {@code AllMandatorySlotsValid})
 * @param isUserEditable         разрешено ли подставлять конкретного пользователя при создании
 *                               процесса
 * @param isOrganizationEditable разрешено ли подставлять организацию
 * @param isDeletable            можно ли удалить слот при работе с маршрутом
 */
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
