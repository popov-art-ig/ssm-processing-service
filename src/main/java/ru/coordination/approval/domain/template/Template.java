package ru.coordination.approval.domain.template;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.coordination.approval.domain.common.BaseEntity;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.common.ProcessType;

/**
 * Многоразовое типовое описание маршрута (корень TemplateAggregate).
 *
 * <p>Версионирование (ADR-023): отдельной сущности {@code TemplateVersion} нет.
 * Публикация новой версии создаёт новую строку {@code Template} со своим id,
 * {@code version = предыдущая + 1} и {@code parentTemplateId}, указывающим на
 * предыдущую версию — та же цепочка, что используется для форка.
 *
 * См. 03_domain_model.md §6.2, 08_db_schema.md §6.1–6.2.
 */
@Entity
@Table(name = "template")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Template extends BaseEntity {

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "process_type", length = 50, nullable = false)
    private ProcessType processType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private LifecycleStatus status;

    @Column(name = "version", nullable = false)
    @lombok.Builder.Default
    private Integer version = 1;

    /** Ссылка на предыдущую версию/родителя форка. Cross-aggregate-style ссылка — хранится как «голый» id. */
    @Column(name = "parent_template_id")
    private UUID parentTemplateId;

    @Column(name = "process_iteration_enabled", nullable = false)
    @lombok.Builder.Default
    private boolean processIterationEnabled = false;

    /** ADR-023: перенесено из удалённой {@code template_version}. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "responsible_roles", columnDefinition = "uuid[]")
    private List<UUID> responsibleRoles;

    @Column(name = "requires_responsible_approval", nullable = false)
    @lombok.Builder.Default
    private boolean requiresResponsibleApproval = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIdx ASC")
    @lombok.Builder.Default
    private List<StageTemplate> stages = new ArrayList<>();

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ruleIdx ASC")
    @lombok.Builder.Default
    private List<ApplicabilityRule> applicabilityRules = new ArrayList<>();
}
