package ru.coordination.approval.domain.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.coordination.approval.domain.common.BaseEntity;

/**
 * Правило применимости шаблона к сущности. См. 03_domain_model.md §6.6, 08_db_schema.md §6.5.
 *
 * <p>Пример {@code attributeConditions}: {@code {"amount": {"gte": 1000000}}}.
 */
@Entity
@Table(name = "applicability_rule")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ApplicabilityRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @Column(name = "rule_idx", nullable = false)
    private Integer ruleIdx;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "entity_types", columnDefinition = "text[]", nullable = false)
    @lombok.Builder.Default
    private List<String> entityTypes = List.of();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "entity_subtypes", columnDefinition = "text[]", nullable = false)
    @lombok.Builder.Default
    private List<String> entitySubtypes = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attribute_conditions", nullable = false)
    @lombok.Builder.Default
    private Map<String, Object> attributeConditions = Map.of();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
