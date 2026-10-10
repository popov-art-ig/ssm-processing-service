package ru.coordination.approval.domain.process;

/**
 * Роль основного участника этапа. См. 03_domain_model.md §7.6.
 *
 * <p>Роль ограничена check-констрейнтом в БД ({@code 08_db_schema.md} §9.1) и выводится из типа
 * слота при генерации маршрута: {@code SlotType.ACTOR} → {@link #APPROVER},
 * {@code SlotType.ADDITIONAL_APPROVER} → {@link #ADDITIONAL_APPROVER}. Ключевым для агрегации
 * решений является именно {@code APPROVER}: строки {@code decision} создаются только для
 * согласующих.
 */
public enum ParticipantRole {

    /** Основной согласующий: выносит решение, участвует в агрегации ({@code evaluateAggregation}). */
    APPROVER,

    /**
     * Подписант. Роль присутствует в схеме и API-маппинге, но из типа слота не выводится —
     * код генерации маршрута выставляет только {@code APPROVER}/{@code ADDITIONAL_APPROVER}.
     */
    SIGNER,

    /**
     * Дополнительный согласующий. Создаётся как обычный {@code Participant} (см. {@code SlotType}),
     * его {@code Participant.decision} нигде не заполняется, а рекомендация по модели должна жить
     * в отдельной сущности {@code AdditionalApprover} — этот путь пока не реализован (PHASE-10).
     */
    ADDITIONAL_APPROVER,

    /**
     * Наблюдатель: роль заявлена для видимости без права голоса. Ни генерация маршрута, ни
     * guards/actions её не назначают и не проверяют.
     */
    OBSERVER
}
