package ru.coordination.approval.domain.audit;

/**
 * Тип инициатора события аудита. См. 03_domain_model.md §9.1 (User / System / Timer —
 * приведено к стандартной Java-конвенции SCREAMING_SNAKE_CASE при хранении как varchar).
 */
public enum ActorType {
    USER,
    SYSTEM,
    TIMER
}
