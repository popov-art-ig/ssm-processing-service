package ru.coordination.approval.domain.registry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.coordination.approval.domain.common.RegistryScope;

/**
 * Запись реестра action-обработчиков, используемых {@code TransitionEngine} (ADR-028).
 * Резолвится по коду через {@code ActionRegistry} (10_architecture.md §7.2.6).
 *
 * См. 08_db_schema.md §16.2, 05_guards_actions_registry.md.
 */
@Entity
@Table(name = "action_registry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActionRegistryEntry {

    @Id
    @Column(name = "code", length = 100, nullable = false)
    private String code;

    @Column(name = "display_name", length = 255, nullable = false)
    private String displayName;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "handler", length = 255, nullable = false)
    private String handler;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params_schema")
    private Map<String, Object> paramsSchema;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", length = 50, nullable = false)
    private RegistryScope scope;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "categories", columnDefinition = "text[]", nullable = false)
    @Builder.Default
    private List<String> categories = List.of();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "applicable_entities", columnDefinition = "text[]", nullable = false)
    @Builder.Default
    private List<String> applicableEntities = List.of();

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
