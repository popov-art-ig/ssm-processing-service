package ru.coordination.approval.api.dto;

public record ErrorResponse(
        String code,
        String message
) {
}
