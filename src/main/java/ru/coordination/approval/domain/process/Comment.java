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
 * Комментарий к процессу (в отличие от {@link Remark} — не имеет статусного жизненного
 * цикла). {@code stageIterationId} — корректная ссылка на {@link StageIteration} (Fix 7.2).
 * Вложения — {@code uuid[]} непосредственно в строке (без {@code comment_attachment}, ADR-026).
 *
 * См. 03_domain_model.md §7.10, 08_db_schema.md §12.3.
 */
@Entity
@Table(name = "comment")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Comment extends BaseEntity {

    /** Процесс, к которому относится комментарий; обязательная привязка верхнего уровня. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id", nullable = false)
    private ProcessInstance process;

    /**
     * Этап, к которому относится комментарий. Единственный писатель — {@code RecordCommentAction}
     * (побочный эффект перехода {@code Decide}), который берёт этап из итерации участника, поэтому
     * поле заполняется всегда, когда комментарий создан движком.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id")
    private StageInstance stage;

    /** Итерация этапа, к которой отнесён комментарий (см. {@link #stage}). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_iteration_id")
    private StageIteration stageIteration;

    /** Участник-автор комментария; заполняется тем же действием из сущности перехода {@code Decide}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    private Participant participant;

    /** Автор комментария «сырым» идентификатором — заполняется всегда, даже когда {@link #participant} пуст. */
    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    /** Роль автора комментария. Сейчас всегда {@link AuthorRole#APPROVER}: {@code RecordComment} не различает роли. */
    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", length = 50, nullable = false)
    private AuthorRole authorRole;

    /** Текст комментария. */
    @Column(name = "text", nullable = false)
    private String text;

    /** Вложенность (дерево ответов). Заполняется вручную: {@code RecordCommentAction} родителей не ставит. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    /**
     * Вложения — идентификаторы файлов внешнего хранилища; модуль их не загружает и не отдаёт,
     * только хранит (ADR-026).
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "attachments", columnDefinition = "uuid[]", nullable = false)
    @lombok.Builder.Default
    private List<UUID> attachments = new ArrayList<>();

    /** Время создания комментария. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
