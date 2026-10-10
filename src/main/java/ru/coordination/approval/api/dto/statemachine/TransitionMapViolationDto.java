package ru.coordination.approval.api.dto.statemachine;

/**
 * Одно нарушение карты переходов.
 *
 * @param code    код нарушения ({@code GUARD_NOT_FOUND}, {@code AMBIGUOUS_TRANSITION}, …)
 * @param where   место: код перехода, состояние или карта целиком
 * @param message описание проблемы и её последствий
 */
public record TransitionMapViolationDto(
        String code,
        String where,
        String message
) {
}
