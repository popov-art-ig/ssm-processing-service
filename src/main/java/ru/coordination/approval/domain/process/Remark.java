package ru.coordination.approval.domain.process;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

/**
 * Замечание к процессу. {@code stageIterationId} — корректная ссылка на {@link StageIteration}
 * (Fix 7.2; было «голое» целое без внешнего ключа). Вложения хранятся как {@code uuid[]}
 * непосредственно в строке, без отдельной таблицы {@code remark_attachment} (ADR-026).
 *
 * См. 03_domain_model.md §7.9, 08_db_schema.md §12.1.
 */
@Entity
@Table(name = "remark")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Remark extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id", nullable = false)
    private ProcessInstance process;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id")
    private StageInstance stage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_iteration_id")
    private StageIteration stageIteration;

    /** Автор-участник (основной или доп. согласующий), если применимо. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    private Participant participant;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", length = 50, nullable = false)
    private AuthorRole authorRole;

    @Column(name = "text", nullable = false)
    private String text;

    /** Валидируется через StatusRegistry (entityType=REMARK). */
    @Column(name = "status", length = 100, nullable = false)
    private String status;

    @Column(name = "activated_by")
    private UUID activatedBy;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "rejected_by")
    private UUID rejectedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "attachments", columnDefinition = "uuid[]", nullable = false)
    @lombok.Builder.Default
    private List<UUID> attachments = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
