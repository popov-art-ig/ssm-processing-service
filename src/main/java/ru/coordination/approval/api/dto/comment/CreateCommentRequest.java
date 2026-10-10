package ru.coordination.approval.api.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Тело запроса на создание комментария ({@code 07_api_contract.md} §18.1). Используется только
 * заглушкой {@code CommentController#createComment}, поэтому ни одно значение отсюда пока не
 * сохраняется.
 *
 * @param stageId  этап, к которому относится комментарий; может быть {@code null} — тогда
 *                 комментарий относится к процессу целиком
 * @param remarkId привязка к замечанию; в модели отсутствует, и при реализации эндпоинта поле
 *                 придётся либо поддержать в схеме, либо убрать из DTO
 */
public record CreateCommentRequest(
        UUID stageId,
        UUID remarkId,
        @NotBlank String text,
        @NotNull UUID authorId
) {
}
