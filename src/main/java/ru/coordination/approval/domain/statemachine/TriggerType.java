package ru.coordination.approval.domain.statemachine;

/**
 * Тип триггера перехода. См. 03_domain_model.md §8.4.
 *
 * <p>Разделяет, кто инициировал переход ({@code 04_state_machines.md} §2.5): пользователь через
 * API, система внутри действия другого перехода или планировщик. Для вызывающего кода это не
 * подсказка, а часть ключа поиска перехода — {@code TransitionEngine} ищет кандидатов по паре
 * {@code (fromState, trigger)}, поэтому один и тот же исход, доступный и пользователю, и
 * системе, объявляется двумя строками {@code transition_config}.
 */
public enum TriggerType {
    USER_ACTION,
    SYSTEM_ACTION,
    TIMER
}
