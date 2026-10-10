package ru.coordination.approval.api.dto.remark;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Тело запроса «отклонить замечание» ({@code 07_api_contract.md} §17.8) — завершение работы с
 * замечанием без исправления.
 *
 * @param reason причина отклонения; сохраняется в {@code Remark.rejectionReason}, а его наличие в
 *               параметрах перехода — это то, по чему guard {@code IsRemarkRejection} отличает
 *               отклонение от обработки замечания
 */
public record RejectRemarkRequest(
        @NotBlank String reason,
        @NotNull UUID actorId
) {
}
