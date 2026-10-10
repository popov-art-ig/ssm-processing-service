package ru.coordination.approval.api.exception;

/**
 * Тело ответа об ошибке REST API ({@code 07_api_contract.md} §4): машиночитаемый {@code code} и
 * текст {@code message} для человека.
 *
 * @param code    строковый код ошибки ({@code VALIDATION_ERROR}, {@code NOT_FOUND},
 *                {@code BAD_REQUEST}, {@code CONFLICT}, {@code INTERNAL_ERROR})
 * @param message описание; для {@code INTERNAL_ERROR} намеренно обезличенное — детали остаются
 *                в логе, чтобы не раскрывать клиенту внутреннее устройство
 */
public record ErrorResponse(
        String code,
        String message
) {
}
