package ru.coordination.approval.api.dto.statemachine;

import java.util.List;
import java.util.UUID;

/**
 * Результат проверки карты переходов ({@code 05_guards_actions_registry.md} §7).
 *
 * @param configId   проверенная конфигурация
 * @param valid      {@code true}, если нарушений нет
 * @param violations список нарушений; пустой, если карта корректна
 */
public record TransitionMapValidationDto(
        UUID configId,
        boolean valid,
        List<TransitionMapViolationDto> violations
) {
}
