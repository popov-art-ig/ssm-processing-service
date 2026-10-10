package ru.coordination.approval.domain.statemachine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.coordination.approval.domain.common.BaseEntity;

/**
 * Переход между состояниями. Guards/actions — коды, резолвящиеся {@code TransitionEngine}
 * через {@code GuardRegistry}/{@code ActionRegistry} (ADR-028, 10_architecture.md §7.2.1,
 * §7.2.5, §7.2.6). Алгоритм отбора и исполнения — 04_state_machines.md §3.3: поиск по
 * (entityType, fromState, trigger, processType) → сортировка по priority → guards → actions.
 *
 * См. 03_domain_model.md §8.4, 08_db_schema.md §15.3.
 */
@Entity
@Table(name = "transition_config")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class TransitionConfig extends BaseEntity {

    /**
     * Карта-владелец перехода. Связь {@code LAZY}: переходы загружаются запросом по
     * {@code config_id}, а не обходом {@code StateMachineConfig.transitions} — см.
     * {@code StateConfigRepository}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false)
    private StateMachineConfig config;

    /**
     * Код перехода — он же {@code AuditEvent.action}. Уникален в рамках
     * {@code (config_id, code, from_state)}: один код осмысленно повторяется для разных исходных
     * состояний (например {@code RecallProcess}), и индекс по одной паре {@code (config_id, code)}
     * молча терял бы второй переход (см. комментарий в {@code V11__state_machine_config.sql}).
     */
    @Column(name = "code", length = 100, nullable = false)
    private String code;

    /**
     * Исходное состояние — половина ключа поиска кандидатов ({@code (from_state, trigger)}).
     * Существование кода состояния в карте проверяет {@code TransitionMapValidator}
     * ({@code STATE_UNKNOWN}), движок этого не проверяет.
     */
    @Column(name = "from_state", length = 100, nullable = false)
    private String fromState;

    /**
     * Целевое состояние: записывается в {@code status} сущности и проверяется по
     * {@code status_registry} ({@code InvalidTargetStatusException}) — карта не может увести
     * сущность в статус, которого нет в реестре.
     */
    @Column(name = "to_state", length = 100, nullable = false)
    private String toState;

    /** Тип триггера — вторая половина ключа поиска; см. {@link TriggerType}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger", length = 50, nullable = false)
    private TriggerType trigger;

    /**
     * Коды guards, резолвящиеся через {@code GuardRegistry}. Проверяются по порядку массива до
     * первого {@code false} — порядок задаёт, какой guard первым отклонит переход, но не выбор
     * перехода между кандидатами.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "guards", columnDefinition = "text[]", nullable = false)
    @lombok.Builder.Default
    private List<String> guards = List.of();

    /**
     * Коды actions, резолвящиеся через {@code ActionRegistry}. Выполняются строго по порядку
     * массива и после записи нового статуса (см. {@code TransitionEngine.execute}) — порядок
     * здесь часть поведения перехода, а не оформление.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "actions", columnDefinition = "text[]", nullable = false)
    @lombok.Builder.Default
    private List<String> actions = List.of();

    /**
     * Коды событий контракта ({@code 06_event_contract.md} §20), которые {@code TransitionEngine}
     * возвращает в {@code TransitionResult}: в PHASE-02 движок их только отдаёт вызывающему коду,
     * физической публикации в outbox/RabbitMQ нет.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "emits", columnDefinition = "text[]", nullable = false)
    @lombok.Builder.Default
    private List<String> emits = List.of();

    /**
     * Приоритет отбора: большее значение проверяется первым ({@code 04_state_machines.md} §3.3,
     * шаг 2). При равном приоритете порядок задаёт лексикографический tie-break по {@code code} —
     * поэтому исход не должен зависеть от равенства приоритетов (см.
     * {@code AMBIGUOUS_TRANSITION}).
     */
    @Column(name = "priority", nullable = false)
    @lombok.Builder.Default
    private Integer priority = 0;

    /**
     * Флаг {@code is_active}: неактивные переходы не попадают в модель
     * ({@code ModelFactory.loadModel} их отсеивает), то есть их нельзя исполнить, не удаляя
     * строку из карты. Ограничение: валидатор проверяет guards/actions/emits и у неактивных
     * переходов, поэтому «временно выключенный» переход с несуществующим guard'ом всё равно
     * заблокирует публикацию карты.
     */
    @Column(name = "is_active", nullable = false)
    @lombok.Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
