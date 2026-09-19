package ru.coordination.approval.domain.statemachine;

/**
 * Тип триггера перехода. См. 03_domain_model.md §8.4.
 */
public enum TriggerType {
    USER_ACTION,
    SYSTEM_ACTION,
    TIMER
}
