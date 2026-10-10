package ru.coordination.approval.engine.validation;

/**
 * Коды нарушений карты переходов (источник: {@code 05_guards_actions_registry.md} §7.2,
 * дополнено по результатам аудита 2026-10-08).
 *
 * <p>Первые четыре кода заданы спецификацией. Остальные добавлены потому, что реальные дефекты,
 * найденные аудитом, спецификацией не покрывались и приводили к молчаливым отказам во время
 * исполнения переходов, а не к ошибке публикации:
 * <ul>
 *   <li>{@code STATE_UNKNOWN} — {@code from_state}/{@code to_state} отсутствует в карте;</li>
 *   <li>{@code INITIAL_STATE_MISSING}, {@code MULTIPLE_INITIAL_STATES} — неоднозначный старт;</li>
 *   <li>{@code DUPLICATE_TRANSITION_CODE} — один код перехода на одно исходное состояние дважды
 *       (в БД это ловит уникальный индекс, но проверка нужна и до записи);</li>
 *   <li>{@code AMBIGUOUS_TRANSITION} — два активных перехода из одной пары
 *       {@code (from_state, trigger)} с разными {@code to_state}. ModelFactory сортирует
 *       кандидатов по {@code priority}, затем по {@code code}, поэтому такой набор даёт
 *       выбор, зависящий от лексикографического порядка кодов, а не от намерения. Именно этот
 *       класс ошибок давал: {@code rejectRemark} → Processed, возобновление → отзыв,
 *       завершение процесса без признака «с замечаниями»;</li>
 *   <li>{@code APPLICABILITY_MISMATCH} — guard/action применяется к сущности, для которой не
 *       объявлен. Например {@code AssignStageTasks} (process-level) в переходе уровня STAGE
 *       приводил к {@code ClassCastException} во время исполнения.</li>
 * </ul>
 */
public enum TransitionMapErrorCode {

    /** Guard не зарегистрирован в {@code guard_registry}. */
    GUARD_NOT_FOUND,

    /** Action не зарегистрирован в {@code action_registry}. */
    ACTION_NOT_FOUND,

    /** Параметры guard/action не соответствуют схеме ({@code params_schema}). */
    PARAMS_INVALID,

    /** Неизвестный тип события в {@code emits}. */
    EVENT_UNKNOWN,

    /** {@code from_state} или {@code to_state} отсутствует среди состояний карты. */
    STATE_UNKNOWN,

    /** В карте нет ни одного состояния с {@code is_initial = true}. */
    INITIAL_STATE_MISSING,

    /** В карте больше одного начального состояния. */
    MULTIPLE_INITIAL_STATES,

    /** Один код перехода объявлен дважды для одного исходного состояния. */
    DUPLICATE_TRANSITION_CODE,

    /** Из одной пары {@code (from_state, trigger)} два активных перехода в разные состояния. */
    AMBIGUOUS_TRANSITION,

    /** Guard/action не применим к {@code entity_type} карты. */
    APPLICABILITY_MISMATCH
}
