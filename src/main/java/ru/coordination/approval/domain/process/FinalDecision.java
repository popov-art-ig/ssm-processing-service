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

    /**
     * Процесс, к которому относится финальное решение. Связь «один к одному» с уникальным
     * индексом: у процесса не может быть двух финальных решений ({@code 08_db_schema.md} §13.1).
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id", nullable = false, unique = true)
    private ProcessInstance process;

    /**
     * Ответственный (только {@code UNIFIED}), принявший финальное решение. Хранится как «голый»
     * id, чтобы решение не зависело от изменений в сервисе пользователей.
     */
    @Column(name = "responsible_user_id", nullable = false)
    private UUID responsibleUserId;

    /** Роль, по которой был разрешён ответственный на момент решения (ADR-010); только для трассировки. */
    @Column(name = "responsible_resolved_role_ref")
    private UUID responsibleResolvedRoleRef;

    /** Код decision_result_registry. */
    @Column(name = "result", length = 100, nullable = false)
    private String result;

    /** Текст финального решения. */
    @Column(name = "comment")
    private String comment;

    /** Момент принятия финального решения. */
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    /** Время создания строки. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
