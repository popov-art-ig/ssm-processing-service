package ru.coordination.approval.domain.common;

/**
 * Жизненный цикл версионируемых конфигураций (шаблоны маршрутов, карты переходов state machine).
 * См. 03_domain_model.md §6.2 (Template.status), §8.2 (StateMachineConfig.status).
 */
public enum LifecycleStatus {
    DRAFT,
    PUBLISHED,
    DEPRECATED,
    ARCHIVED
}
