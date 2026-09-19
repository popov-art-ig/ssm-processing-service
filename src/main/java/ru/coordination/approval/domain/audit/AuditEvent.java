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

    @Column(name = "entity_type", length = 100, nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "action", length = 100, nullable = false)
    private String action;

    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", length = 50)
    private ActorType actorType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    @lombok.Builder.Default
    private Map<String, Object> payload = Map.of();

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
