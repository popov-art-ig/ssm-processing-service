package ru.coordination.approval.api.dto;

/**
 * Тело ответа об ошибке ({@code 07_api_contract.md} §9.1) — пара «машиночитаемый код + текст».
 * Именно этот тип возвращает {@code GlobalExceptionHandler}; клиенту следует ветвиться по
 * {@code code}, а не по HTTP-статусу.
 *
 * <p><b>Дубликат-ловушка:</b> в {@code api.exception} есть одноимённый record с теми же
 * компонентами. Контроллеры объявляют его в Swagger-схемах ({@code @Schema(implementation =
 * ErrorResponse.class)}) и импортируют именно версию из {@code api.exception}, а в тело ответа
 * попадает эта. Формы совпадают, поэтому расхождение не видно в рантайме, но при правке одного
 * класса документация перестанет соответствовать ответу.
 *
 * @param code    код ошибки: {@code VALIDATION_ERROR}, {@code NOT_FOUND}, {@code BAD_REQUEST},
 *                {@code CONFLICT}, {@code INTERNAL_ERROR}
 * @param message описание для человека; для {@code 500} намеренно обезличено, причина уходит в лог
 */
public record ErrorResponse(
        String code,
        String message
) {
}
