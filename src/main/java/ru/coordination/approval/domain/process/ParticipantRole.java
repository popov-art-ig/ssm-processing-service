package ru.coordination.approval.domain.process;

/**
 * Роль основного участника этапа. См. 03_domain_model.md §7.6.
 */
public enum ParticipantRole {
    APPROVER,
    SIGNER,
    ADDITIONAL_APPROVER,
    OBSERVER
}
