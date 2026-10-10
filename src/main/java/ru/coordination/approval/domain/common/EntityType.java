package ru.coordination.approval.domain.common;

/**
 * Тип сущности, к которой применяется машина состояний / реестр статусов.
 * См. 03_domain_model.md §5.2 (StatusRegistry.entityType), §8.2 (StateMachineConfig.entityType).
 *
 * <p>Связующее звено трёх таблиц: {@code StateMachineConfig} описывает карту переходов
 * <i>на тип сущности</i> ({@code 08_db_schema.md} §15.1), {@code StatusRegistry} — допустимые
 * статусы на пару {@code (code, entityType)} (§5.1), а {@code TransitionEngine} при переходе
 * сверяет {@code entityType} карты с типом переданной сущности, чтобы не применить карту
 * процесса к этапу.
 */
public enum EntityType {

    /** Процесс согласования — {@link ru.coordination.approval.domain.process.ProcessInstance}. */
    PROCESS,

    /** Этап маршрута — {@link ru.coordination.approval.domain.process.StageInstance}. */
    STAGE,

    /**
     * Итерация этапа — {@link ru.coordination.approval.domain.process.StageIteration}. Собственной
     * карты переходов у этого типа нет (ни одна миграция не создаёт
     * {@code state_machine_config} с таким {@code entity_type}): итерация меняет статус как
     * побочный эффект переходов этапа, а не собственным переходом.
     */
    STAGE_ITERATION,

    /** Участник — {@link ru.coordination.approval.domain.process.Participant} (карта V20, PHASE-05). */
    PARTICIPANT,

    /**
     * Дополнительный согласующий — {@link ru.coordination.approval.domain.process.AdditionalApprover}.
     * Тип объявлен в модели и схеме, но карты переходов для него пока нет: иерархия доп.
     * согласующих (PHASE-10) не реализована, статус остаётся начальным.
     */
    ADDITIONAL_APPROVER,

    /**
     * Финальное решение ответственного — {@link ru.coordination.approval.domain.process.FinalDecision}.
     * Карты переходов нет: {@code FinalDecision} иммутабельно
     * ({@code 03_domain_model.md} §10.1) и не меняет состояние через движок.
     */
    FINAL_DECISION,

    /** Замечание — {@link ru.coordination.approval.domain.process.Remark} (PHASE-11, карта V24). */
    REMARK,

    /**
     * Шаблон маршрута — {@link ru.coordination.approval.domain.template.Template}. Добавлен
     * вместе с жизненным циклом шаблонов (PHASE-14, карта {@code TemplateStateMachine}, V25);
     * в {@code 08_db_schema.md} §15.1 набор значений {@code state_machine_config.entity_type}
     * этот тип ещё не перечисляет.
     */
    TEMPLATE
}
