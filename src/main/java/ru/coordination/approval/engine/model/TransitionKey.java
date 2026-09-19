package ru.coordination.approval.engine.model;

import ru.coordination.approval.domain.statemachine.TriggerType;

/**
 * Ключ поиска переходов в {@link TransitionModel}: (fromState, trigger) — шаг 1 алгоритма
 * 04_state_machines.md §3.3, скопированного в PHASE-02.
 */
public record TransitionKey(String fromState, TriggerType trigger) {
}
