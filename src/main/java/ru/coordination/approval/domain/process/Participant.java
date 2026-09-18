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

/**
 * Конкретный сотрудник, назначенный на слот основного участника или подписанта.
 *
 * <p>{@code orderIdx} определяет очередь при {@code executionOrder = SEQUENTIAL} этапа
 * (ADR-022): участники получают задачи по возрастанию {@code orderIdx}, следующий — только
 * после того, как предыдущий принял решение или автосогласован. При {@code PARALLEL} поле
 * не влияет на назначение задач.
 *
 * <p>{@code actorSlotRef}/{@code resolvedRoleRef} — ссылки на сущности TemplateAggregate
 * (другого агрегата); хранятся как «голые» id, а не JPA-связи.
 *
 * См. 03_domain_model.md §7.6, 08_db_schema.md §9.1.
 */
@Entity
@Table(name = "participant")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Participant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_iteration_id", nullable = false)
    private StageIteration stageIteration;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "organization_id")
    private UUID organizationId;

    /** Ссылка на {@code slot_template.id} (Fix 7.3) — другой агрегат, поэтому «голый» id. */
    @Column(name = "actor_slot_ref")
    private UUID actorSlotRef;

    @Column(name = "resolved_role_ref")
    private UUID resolvedRoleRef;

    @Column(name = "order_idx", nullable = false)
    @lombok.Builder.Default
    private Integer orderIdx = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 50, nullable = false)
    private ParticipantRole role;

    /** Pending / Assigned / InProgress / Decided / AutoApproved / Revoked / Cancelled — через StatusRegistry. */
    @Column(name = "status", length = 100, nullable = false)
    private String status;

    /** Ссылка на код {@code decision_result_registry}, не JPA-связь на {@link Decision}. */
    @Column(name = "decision", length = 100)
    private String decision;

    @Column(name = "is_user_editable", nullable = false)
    @lombok.Builder.Default
    private boolean userEditable = true;

    @Column(name = "is_organization_editable", nullable = false)
    @lombok.Builder.Default
    private boolean organizationEditable = false;

    @Column(name = "is_deletable", nullable = false)
    @lombok.Builder.Default
    private boolean deletable = false;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "participant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @lombok.Builder.Default
    private List<AdditionalApprover> additionalApprovers = new ArrayList<>();
}
