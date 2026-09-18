package ru.coordination.approval.domain.common;

/**
 * Режим агрегации решений участников этапа (ADR-022).
 * Реализация — в guards/actions, см. 05_guards_actions_registry.md §4.2, 04_state_machines.md §5.2, §7.
 */
public enum DecisionMode {
    AND,
    ANY_APPROVE,
    ANY_REJECT,
    ANY_DECISION,
    FIRST_REJECT_FAIL_FAST
}
