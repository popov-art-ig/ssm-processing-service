package ru.coordination.approval.domain.process;

/**
 * Роль автора замечания/комментария. См. 03_domain_model.md §7.9, §7.10.
 * Примечание: для {@code Comment} доменная модель не перечисляет конкретные значения —
 * повторно используется тот же набор, что и для {@code Remark}, до уточнения в следующих фазах.
 */
public enum AuthorRole {
    APPROVER,
    ADDITIONAL_APPROVER
}
