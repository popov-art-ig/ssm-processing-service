package ru.coordination.approval.engine;

import java.util.List;

/**
 * Результат {@link TransitionEngine#transition}. {@code performed = false} означает, что
 * подходящие переходы для {@code (fromState, trigger)} существуют, но ни один не прошёл
 * guards (шаг 4 алгоритма при непустом наборе кандидатов) — это ожидаемый бизнес-результат,
 * а не ошибка. Полное отсутствие конфигурации для {@code (fromState, trigger)} — это
 * {@link ru.coordination.approval.engine.exception.NoApplicableTransitionException}, не этот
 * результат (см. «Открытые вопросы» п.1 тикета PHASE-02).
 */
public record TransitionResult(
        boolean performed,
        String fromState,
        String toState,
        String transitionCode,
        List<String> emittedEvents) {

    public static TransitionResult performed(
            String fromState, String toState, String transitionCode, List<String> emittedEvents) {
        return new TransitionResult(true, fromState, toState, transitionCode, List.copyOf(emittedEvents));
    }

    public static TransitionResult notPerformed(String fromState) {
        return new TransitionResult(false, fromState, null, null, List.of());
    }
}
