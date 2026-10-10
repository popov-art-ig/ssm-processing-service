package ru.coordination.approval.domain.common;

/**
 * Порядок работы участников на этапе (ADR-022).
 *
 * <p>Хранится в {@code stage_template.execution_order} ({@code 08_db_schema.md} §6.3) и
 * копируется в {@code stage_instance.execution_order} (§8.1); при {@code STANDARD} обязателен,
 * при {@code UNIFIED} запрещён — это проверяет action
 * {@link ru.coordination.approval.service.action.ValidateTemplateStructureAction} перед
 * публикацией шаблона (PHASE-14).
 */
public enum ExecutionOrder {

    /**
     * Все участники итерации получают задачу сразу (действие {@code A-S-001
     * AssignParticipantTasks} переводит их в {@code Assigned}). Это же поведение применяется,
     * когда поле у этапа не задано — ветка {@code else} в
     * {@link ru.coordination.approval.service.action.AssignParticipantTasksAction}.
     */
    PARALLEL,

    /**
     * Задачи выдаются по очереди: первым по {@code orderIdx} назначается только один участник,
     * остальные остаются в статусе {@code Pending} и получают задачу после решения предыдущего
     * (guard {@code G-PA-007 CanAssignTask}, действие {@code A-S-009 AssignNextParticipantTask}).
     */
    SEQUENTIAL
}
