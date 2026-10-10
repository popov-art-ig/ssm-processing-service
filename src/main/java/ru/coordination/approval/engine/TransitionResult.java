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

    /**
     * Результат выполненного перехода. {@code emittedEvents} копируется: список приходит из
     * {@code transition_config.emits} управляемой JPA-сущности, и результат не должен давать
     * возможность менять её через себя.
     */
    public static TransitionResult performed(
            String fromState, String toState, String transitionCode, List<String> emittedEvents) {
        return new TransitionResult(true, fromState, toState, transitionCode, List.copyOf(emittedEvents));
    }

    /**
     * Результат «ни один кандидат не прошёл guards»: {@code toState} и {@code transitionCode}
     * остаются {@code null} — перехода не было, и подставлять сюда исходное состояние как
     * целевое нельзя, иначе вызывающий код примет отказ за переход «в себя».
     */
    public static TransitionResult notPerformed(String fromState) {
        return new TransitionResult(false, fromState, null, null, List.of());
    }
}
