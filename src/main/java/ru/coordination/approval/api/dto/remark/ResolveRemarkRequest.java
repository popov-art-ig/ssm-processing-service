package ru.coordination.approval.api.dto.remark;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Тело запроса «исправить замечание» ({@code 07_api_contract.md} §17.7) — переход замечания в
 * обработанное состояние.
 *
 * @param resolutionText описание исправления; в параметрах перехода {@code ProcessRemark} поле
 *                       называется {@code resolution} и читается guard'ом {@code IsRemarkResolution},
 *                       который отличает обработку от отклонения
 * @param actorId        кто исправил: попадёт в {@code resolvedBy}, когда эндпоинт реализуют
 */
public record ResolveRemarkRequest(
        @NotBlank String resolutionText,
        @NotNull UUID actorId
) {
}
