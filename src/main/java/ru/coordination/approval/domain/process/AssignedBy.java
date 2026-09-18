package ru.coordination.approval.domain.process;

/**
 * Кем назначен дополнительный согласующий. См. 03_domain_model.md §7.7.
 */
public enum AssignedBy {
    TEMPLATE,
    INITIATOR,
    PARTICIPANT,
    ADDITIONAL_APPROVER
}
