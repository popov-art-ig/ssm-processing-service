package ru.coordination.approval.domain.registry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Справочник всех возможных статусов для сущностей (RegistryAggregate).
 * С ADR-028 — единственный валидатор {@code status}-полей всех агрегатов
 * (ранее статус дублировался в {@code ssm_state_machine_context.state}).
 *
 * См. 03_domain_model.md §5.2, 08_db_schema.md §5.1.
 */
@Entity
@Table(name = "status_registry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusRegistry {

    /** Код статуса. Уникален в рамках {@link #entityType}. */
    @Id
    @Column(name = "code", length = 100, nullable = false)
    private String code;

    @Column(name = "entity_type", length = 50, nullable = false)
    private String entityType;

    @Column(name = "display_name", length = 255, nullable = false)
    private String displayName;

    @Column(name = "description")
    private String description;

    @Column(name = "is_terminal", nullable = false)
    @Builder.Default
    private boolean terminal = false;

    @Column(name = "category", length = 50)
    private String category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
