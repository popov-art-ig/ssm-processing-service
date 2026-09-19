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
 * Справочник результатов решений (RegistryAggregate).
 * Значения по умолчанию: APPROVE, APPROVE_WITH_COMMENTS, REJECT (ABSTAIN убран, см. 08_db_schema.md §5.2).
 *
 * См. 03_domain_model.md §5.3, 08_db_schema.md §5.2.
 */
@Entity
@Table(name = "decision_result_registry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionResultRegistry {

    @Id
    @Column(name = "code", length = 100, nullable = false)
    private String code;

    @Column(name = "display_name", length = 255, nullable = false)
    private String displayName;

    @Column(name = "description")
    private String description;

    @Column(name = "is_positive", nullable = false)
    @Builder.Default
    private boolean positive = false;

    @Column(name = "is_negative", nullable = false)
    @Builder.Default
    private boolean negative = false;

    @Column(name = "is_terminal", nullable = false)
    @Builder.Default
    private boolean terminal = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
