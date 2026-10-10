package ru.coordination.approval.api.dto.process;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Тело запроса отзыва процесса ({@code 07_api_contract.md} §12.8) — переход
 * {@code InProgress/OnRework → Recalled}.
 *
 * <p>{@code reason} намеренно необязателен: guard {@code IsRecallRequest} отличает отзыв от
 * возобновления именно по наличию этого параметра в карте перехода (из {@code OnRework} доступны
 * оба), поэтому отсутствие причины не мешает отзыву.
 */
public record RecallProcessRequest(
        @NotNull UUID actorId,
        /** Необязательная причина отзыва; попадает в {@code audit_event.payload} перехода. */
        String reason
) {
}
