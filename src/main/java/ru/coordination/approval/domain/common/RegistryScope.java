package ru.coordination.approval.domain.common;

/**
 * Область видимости записи реестра guard/action. См. 08_db_schema.md §16.
 *
 * <p>Значение колонки {@code scope} в {@code guard_registry}/{@code action_registry}
 * ({@code 05_guards_actions_registry.md} §3.2).
 */
public enum RegistryScope {

    /** Встроенный компонент движка: поставляется вместе с сервисом и резолвится по коду. */
    GLOBAL,

    /**
     * Пользовательское условие/действие: требует явной регистрации вместе с реализацией.
     * В текущих миграциях ни одна запись реестра не помечена {@code CUSTOM} — механизм
     * заявлен, но не заполнен ({@code 05_guards_actions_registry.md} §8).
     */
    CUSTOM
}
