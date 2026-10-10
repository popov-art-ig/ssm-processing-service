package ru.coordination.approval.api.dto.process;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Тело запроса возобновления процесса после доработки ({@code 07_api_contract.md} §12.9) — переход
 * {@code OnRework → InProgress} с активацией выбранного этапа.
 *
 * @param targetStageId этап, на который возвращается процесс; контроллер переводит его в
 *                      {@code orderIdx}, потому что переход {@code ResumeProcess} и guard
 *                      {@code IsTargetStageAllowedGuard} работают с номерами этапов, а не с их id
 */
public record ResumeProcessRequest(
        @NotNull UUID targetStageId,
        @NotNull UUID actorId
) {
}
