package ru.coordination.approval.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.coordination.approval.domain.common.BaseEntity;

/**
 * Запись аудита изменения сущности — корень AuditAggregate (append-only, партиционируется
 * по {@code occurredAt}, месяц; см. 08_db_schema.md §25). Пишется {@code TransitionEngine}
 * в той же транзакции, что и переход/действия (10_architecture.md §7.2.1, ADR-028).
 *
 * См. 03_domain_model.md §9.1, 08_db_schema.md §21.1.
 */
@Entity
@Table(name = "audit_event")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AuditEvent extends BaseEntity {

    /**
     * Тип сущности события — значение {@link ru.coordination.approval.domain.common.EntityType}
     * в виде строки (например {@code PROCESS} или {@code STAGE}).
     */
    @Column(name = "entity_type", length = 100, nullable = false)
    private String entityType;

    /**
     * Идентификатор сущности, к которой относится событие. Внешнего ключа нет намеренно: аудит
     * переживает удаление сущности, а партиционированная таблица не может ссылаться на четыре
     * разные таблицы сразу.
     */
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    /**
     * Что произошло. {@code TransitionEngine} пишет сюда код перехода
     * ({@code transition_config.code}, например {@code StartProcess}), а не имя метода или
     * тип действия — то есть значение совпадает с кодом из карты переходов.
     */
    @Column(name = "action", length = 100, nullable = false)
    private String action;

    /**
     * Инициатор события: пользователь из {@code TransitionContext.actorId()} либо {@code null}
     * для системных переходов без актора.
     */
    @Column(name = "actor_id")
    private UUID actorId;

    /** Тип инициатора — см. {@link ActorType}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", length = 50)
    private ActorType actorType;

    /**
     * Контекст перехода «как есть». {@code TransitionEngine} складывает сюда
     * {@code fromState}, {@code toState}, {@code trigger} и {@code emits} перехода
     * ({@code 06_event_contract.md}); параметры actions в payload не попадают.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    @lombok.Builder.Default
    private Map<String, Object> payload = Map.of();

    /**
     * Время события — и ключ партиционирования таблицы по месяцам
     * ({@code 08_db_schema.md} §21.1, §25), поэтому partitioning работает только при
     * неизменном {@code occurredAt} после вставки.
     */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
