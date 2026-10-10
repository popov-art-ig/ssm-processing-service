package ru.coordination.approval.domain.common;

/**
 * Жизненный цикл версионируемых конфигураций (шаблоны маршрутов, карты переходов state machine).
 * См. 03_domain_model.md §6.2 (Template.status), §8.2 (StateMachineConfig.status).
 *
 * <p>Последовательность строго линейная ({@code DRAFT → PUBLISHED → DEPRECATED → ARCHIVED}),
 * без обратных переходов; публикацию новой версии обслуживает не смена статуса, а новая строка
 * с {@code version + 1} и {@code parent_template_id} (ADR-023, {@code 08_db_schema.md} §6.2).
 */
public enum LifecycleStatus {

    /** Черновик: карта/шаблон не участвует в старте процессов и не проверяется валидатором. */
    DRAFT,

    /**
     * Опубликован: карта используется {@code TransitionEngine}, а карты этого статуса
     * валидируются при старте приложения; шаблон доступен для подбора правилами применимости.
     */
    PUBLISHED,

    /** Снят с публикации: новые процессы по нему не создаются, уже начатые продолжаются. */
    DEPRECATED,

    /**
     * Архивирован: терминальный статус версии. Для шаблона переход разрешает guard
     * {@code NoActiveProcessesGuard} — в архиве не должно остаться ссылающихся процессов.
     */
    ARCHIVED
}
