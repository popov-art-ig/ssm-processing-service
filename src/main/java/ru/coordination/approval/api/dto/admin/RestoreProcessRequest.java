package ru.coordination.approval.api.dto.admin;

import java.util.UUID;

/**
 * Тело запроса восстановления процесса из архива ({@code 07_api_contract.md} §22.2).
 *
 * <p><b>Дубликат</b> {@code api.dto.process.RestoreProcessRequest}: компоненты те же, но здесь
 * {@code actorId} без {@code @NotNull}. Использует
 * {@link ru.coordination.approval.api.controller.AdminController}, а версия из {@code dto.process}
 * не используется нигде — при реализации эндпоинта один из классов надо удалить, и логичнее
 * оставить валидируемый.
 *
 * @param actorId кто восстанавливает процесс; при реализации попадёт в
 *                {@code archive_metadata.restored_by}, а сейчас не сохраняется нигде
 */
public record RestoreProcessRequest(
        UUID actorId
) {
}
