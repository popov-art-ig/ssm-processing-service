package ru.coordination.approval.domain.template;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.coordination.approval.domain.common.BaseEntity;
import ru.coordination.approval.domain.common.DecisionMode;
import ru.coordination.approval.domain.common.ExecutionOrder;
import ru.coordination.approval.domain.common.StageType;

/**
 * Описание одного этапа в шаблоне маршрута.
 * См. 03_domain_model.md §6.4, 08_db_schema.md §6.3.
 */
@Entity
@Table(name = "stage_template")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class StageTemplate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @Column(name = "order_idx", nullable = false)
    private Integer orderIdx;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_type", length = 50, nullable = false)
    private StageType stageType;

    @Column(name = "duration")
    private Integer duration;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_mode", length = 50)
    private DecisionMode decisionMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_order", length = 50)
    private ExecutionOrder executionOrder;

    @Column(name = "is_mandatory", nullable = false)
    @lombok.Builder.Default
    private boolean mandatory = false;

    @Column(name = "is_order_mandatory", nullable = false)
    @lombok.Builder.Default
    private boolean orderMandatory = false;

    /** Список orderIdx этапов, на которые допустим возврат. null/{} — только текущий этап. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_return_stages", columnDefinition = "integer[]")
    private List<Integer> allowedReturnStages;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "stageTemplate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIdx ASC")
    @lombok.Builder.Default
    private List<SlotTemplate> actorSlots = new ArrayList<>();
}
