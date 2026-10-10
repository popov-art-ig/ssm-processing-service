package ru.coordination.approval.api.dto.remark;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Тело запроса на создание замечания ({@code 07_api_contract.md} §17.1). Используется только
 * заглушкой {@code RemarkController#createRemark}.
 *
 * @param stageId  этап, к которому относится замечание; в модели замечание привязывается ещё и к
 *                 итерации этапа, но итерация в запросе не передаётся
 * @param severity степень важности {@code CRITICAL}/{@code MAJOR}/{@code MINOR}; принимается
 *                 строкой, но одноимённой колонки в {@code remark} нет — значение сейчас некуда
 *                 сохранять
 */
public record CreateRemarkRequest(
        @NotNull UUID stageId,
        @NotBlank String text,
        @NotBlank String severity,
        @NotNull UUID authorId
) {
}
