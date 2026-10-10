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

    /** Процесс, к которому относится замечание; обязательная привязка верхнего уровня. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id", nullable = false)
    private ProcessInstance process;

    /**
     * Этап, на котором замечание было высказано. Может быть пустым (замечание к процессу в целом):
     * {@code RemarkService} при создании замечания подставляет этап того участника, от которого
     * пришёл вызов, но схема это не требует.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id")
    private StageInstance stage;

    /** Конкретный круг этапа, к которому относится замечание; см. Javadoc класса (Fix 7.2). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_iteration_id")
    private StageIteration stageIteration;

    /** Автор-участник (основной или доп. согласующий), если применимо. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    private Participant participant;

    /**
     * Автор замечания «сырым» идентификатором: пользователь или инициатор, для которого участника
     * в процессе нет (например замечание инициатора вне задач) — тогда {@link #participant} пуст,
     * а автор известен только по id.
     */
    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    /** Роль автора — позволяет отличить замечание основного согласующего от доп. согласующего. */
    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", length = 50, nullable = false)
    private AuthorRole authorRole;

    /** Текст замечания. */
    @Column(name = "text", nullable = false)
    private String text;

    /** Валидируется через StatusRegistry (entityType=REMARK). */
    @Column(name = "status", length = 100, nullable = false)
    private String status;

    /** Кто перевёл замечание в активное состояние (действие {@code AssignRemarkToAuthor}). */
    @Column(name = "activated_by")
    private UUID activatedBy;

    /** Когда замечание стало активным. */
    @Column(name = "activated_at")
    private Instant activatedAt;

    /**
     * Кто отметил замечание исправленным. Поле не заполняется кодом: переход {@code ProcessRemark}
     * выполняет лишь {@code NotifyRemarkStatusChange} (заглушка), отдельного действия
     * {@code MarkResolved} нет (PHASE-11).
     */
    @Column(name = "resolved_by")
    private UUID resolvedBy;

    /**
     * Когда замечание было исправлено. Кодом не заполняется — как и {@link #resolvedBy}:
     * факт обработки виден только по статусу {@code Processed}.
     */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** Кто отклонил исправление или само замечание; кодом не заполняется (см. {@link #resolvedBy}). */
    @Column(name = "rejected_by")
    private UUID rejectedBy;

    /** Когда замечание/исправление было отклонено; кодом не заполняется. */
    @Column(name = "rejected_at")
    private Instant rejectedAt;

    /**
     * Причина отклонения. Передаётся в параметре {@code reason} перехода {@code RejectRemark} (им
     * же guard {@code IsRemarkRejection} отличает отклонение от обработки), но в это поле не
     * сохраняется — ни одно действие его не пишет.
     */
    @Column(name = "rejection_reason")
    private String rejectionReason;

    /**
     * Вложения — идентификаторы файлов во внешнем хранилище. Список пуст по умолчанию: модуль
     * файлы не принимает, он лишь хранит ссылки (ADR-026).
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "attachments", columnDefinition = "uuid[]", nullable = false)
    @lombok.Builder.Default
    private List<UUID> attachments = new ArrayList<>();

    /** Время создания замечания. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Время последнего изменения замечания (смена статуса). */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
