package ru.coordination.approval.api.exception;

public record ErrorResponse(
        String code,
        String message
) {
}
