package ru.coordination.approval.domain.common;

/**
 * Тип сущности, к которой применяется машина состояний / реестр статусов.
 * См. 03_domain_model.md §5.2 (StatusRegistry.entityType), §8.2 (StateMachineConfig.entityType).
 */
public enum EntityType {
    PROCESS,
    STAGE,
    STAGE_ITERATION,
    PARTICIPANT,
    ADDITIONAL_APPROVER,
    FINAL_DECISION,
    REMARK
}
