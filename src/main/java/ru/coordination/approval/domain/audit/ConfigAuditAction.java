package ru.coordination.approval.domain.audit;

/**
 * Тип действия над конфигурацией state machine. См. 03_domain_model.md §9.2, 08_db_schema.md §21.2.
 */
public enum ConfigAuditAction {
    CREATED,
    UPDATED,
    PUBLISHED,
    DEPRECATED,
    ARCHIVED
}
