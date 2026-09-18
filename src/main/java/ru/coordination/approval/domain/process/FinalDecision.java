package ru.coordination.approval.domain.process;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import ru.coordination.approval.domain.common.BaseEntity;

/**
 * Финальное решение ответственного (UNIFIED). Иммутабельно — операций изменения нет.
 * См. 03_domain_model.md §7.11, 08_db_schema.md §13.1.
 */
@Entity
@Table(name = "final_decision")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class FinalDecision extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id", nullable = false, unique = true)
    private ProcessInstance process;

    @Column(name = "responsible_user_id", nullable = false)
    private UUID responsibleUserId;

    @Column(name = "responsible_resolved_role_ref")
    private UUID responsibleResolvedRoleRef;

    /** Код decision_result_registry. */
    @Column(name = "result", length = 100, nullable = false)
    private String result;

    @Column(name = "comment")
    private String comment;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
