package ru.coordination.approval.domain.common;

/**
 * Тип этапа маршрута. См. 03_domain_model.md §6.4 (StageTemplate), §7.3 (StageInstance).
 */
public enum StageType {
    APPROVAL,
    SIGNING,
    ENDORSEMENT
}
