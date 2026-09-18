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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import ru.coordination.approval.domain.common.BaseEntity;
import ru.coordination.approval.domain.common.DueOffset;

/**
 * Дополнительный согласующий с неограниченной иерархией (level вычисляется как
 * {@code parent.level + 1}, либо 1, если родителя нет).
 *
 * <p><b>Важно (ADR-016, Fix 3).</b> Не имеет связи с {@link Decision} — рекомендация хранится
 * в собственном поле {@link #recommendation}. Не участвует в агрегации решения этапа
 * ({@code evaluateAggregation}) и не управляет переходами state machine — см.
 * 03_domain_model.md §7.8, 08_db_schema.md §10.1.
 *
 * См. 03_domain_model.md §7.7.
 */
@Entity
@Table(name = "additional_approver")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class AdditionalApprover extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_additional_approver_id")
    private AdditionalApprover parentAdditionalApprover;

    @Column(name = "level", nullable = false)
    @lombok.Builder.Default
    private Integer level = 1;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_by", length = 50, nullable = false)
    private AssignedBy assignedBy;

    @Column(name = "assigned_by_user_id")
    private UUID assignedByUserId;

    @Column(name = "due_at")
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "due_offset", length = 10, nullable = false)
    @lombok.Builder.Default
    private DueOffset dueOffset = DueOffset.H0;

    /** Валидируется через StatusRegistry (entityType=ADDITIONAL_APPROVER). */
    @Column(name = "status", length = 100, nullable = false)
    private String status;

    /** Ссылка на код decision_result_registry — информационная, не влияет на state machine. */
    @Column(name = "recommendation", length = 100)
    private String recommendation;

    @Column(name = "is_user_editable", nullable = false)
    @lombok.Builder.Default
    private boolean userEditable = true;

    @Column(name = "is_organization_editable", nullable = false)
    @lombok.Builder.Default
    private boolean organizationEditable = false;

    @Column(name = "is_deletable", nullable = false)
    @lombok.Builder.Default
    private boolean deletable = true;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "recommended_at")
    private Instant recommendedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "parentAdditionalApprover", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @lombok.Builder.Default
    private List<AdditionalApprover> children = new ArrayList<>();
}
