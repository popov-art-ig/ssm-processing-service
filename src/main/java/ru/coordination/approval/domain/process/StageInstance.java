package ru.coordination.approval.domain.process;

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
 * Этап в маршруте процесса. См. 03_domain_model.md §7.3, 08_db_schema.md §8.1.
 */
@Entity
@Table(name = "stage_instance")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class StageInstance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id", nullable = false)
    private ProcessInstance process;

    @Column(name = "order_idx", nullable = false)
    private Integer orderIdx;

    @Column(name = "original_order_idx", nullable = false)
    private Integer originalOrderIdx;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_type", length = 50, nullable = false)
    private StageType stageType;

    @Column(name = "duration", nullable = false)
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

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_return_stages", columnDefinition = "integer[]")
    private List<Integer> allowedReturnStages;

    /** Валидируется через {@code StatusRegistry} (entityType=STAGE). */
    @Column(name = "status", length = 100, nullable = false)
    private String status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "stage", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("iterationIdx ASC")
    @lombok.Builder.Default
    private List<StageIteration> iterations = new ArrayList<>();
}
