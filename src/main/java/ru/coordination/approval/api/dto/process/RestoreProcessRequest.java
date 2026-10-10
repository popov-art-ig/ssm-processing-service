package ru.coordination.approval.api.dto.process;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Тело запроса восстановления процесса из архива ({@code 07_api_contract.md} §22.2) с
 * обязательным {@code actorId}.
 *
 * <p><b>Не используется:</b> {@link ru.coordination.approval.api.controller.AdminController}
 * импортирует одноимённый DTO из {@code api.dto.admin}, где {@code actorId} необязателен. Разница
 * только в {@code @NotNull}, поэтому два класса легко перепутать при правках.
 */
public record RestoreProcessRequest(
        @NotNull UUID actorId
) {
}
