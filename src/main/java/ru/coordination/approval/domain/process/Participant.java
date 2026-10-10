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

    /** Итерация, в рамках которой участник получил задачу; уникальность участника — в её границах. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_iteration_id", nullable = false)
    private StageIteration stageIteration;

    /** Пользователь-исполнитель задачи (идентификатор из внешнего сервиса пользователей). */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Организация участника. Заполняется при клонировании участника в новую итерацию, но при
     * создании маршрута в него ничего не пишется: {@code RouteGenerationService} переносит из слота
     * только пользователя и роль.
     */
    @Column(name = "organization_id")
    private UUID organizationId;

    /** Ссылка на {@code slot_template.id} (Fix 7.3) — другой агрегат, поэтому «голый» id. */
    @Column(name = "actor_slot_ref")
    private UUID actorSlotRef;

    /**
     * Идентификатор роли, по которой был разрешён участник (ADR-010). При генерации маршрута в поле
     * попадает первая роль слота ({@code acceptableRoles.get(0)}); при возобновлении процесса
     * значение копируется из предыдущей итерации.
     */
    @Column(name = "resolved_role_ref")
    private UUID resolvedRoleRef;

    /**
     * Порядок работы участника внутри итерации: при {@code executionOrder = SEQUENTIAL} задача
     * выдаётся по возрастанию {@code orderIdx} (ADR-022). Значение по умолчанию 0 задано только
     * для случаев ручного создания участника — при генерации маршрута поле заполняется из слота.
     */
    @Column(name = "order_idx", nullable = false)
    @lombok.Builder.Default
    private Integer orderIdx = 0;

    /**
     * Роль участника. Определяется типом слота при генерации маршрута
     * ({@code RouteGenerationService.mapSlotTypeToRole}: {@code ACTOR} → {@code APPROVER}) и
     * копируется при создании новой итерации; в БД дополнительно ограничена check-констрейнтом.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 50, nullable = false)
    private ParticipantRole role;

    /** Pending / Assigned / InProgress / Decided / AutoApproved / Revoked / Cancelled — через StatusRegistry. */
    @Column(name = "status", length = 100, nullable = false)
    private String status;

    /**
     * Ссылка на код {@code decision_result_registry}, не JPA-связь на {@link Decision}.
     *
     * <p><b>Известное ограничение.</b> Поле объявлено моделью и схемой, но ни один компонент
     * приложения его не заполняет и не читает: действие {@code RecordDecision} пишет отдельную
     * строку {@link Decision}, а агрегация опирается только на неё. Фактически дубликат
     * {@code decision.result}, оставшийся незадействованным.
     */
    @Column(name = "decision", length = 100)
    private String decision;

    /**
     * Разрешено ли менять пользователя задачи в UI. Флаг только хранится: код его не читает
     * (аналогично {@code organization_id}).
     */
    @Column(name = "is_user_editable", nullable = false)
    @lombok.Builder.Default
    private boolean userEditable = true;

    /** Разрешено ли менять организацию задачи в UI; кодом не читается. */
    @Column(name = "is_organization_editable", nullable = false)
    @lombok.Builder.Default
    private boolean organizationEditable = false;

    /** Разрешено ли удалять задачу в UI; кодом не читается. */
    @Column(name = "is_deletable", nullable = false)
    @lombok.Builder.Default
    private boolean deletable = false;

    /**
     * Время выдачи задачи. Участник в статусе {@code Pending} (последовательный порядок работы)
     * задачи ещё не получил, поэтому поле остаётся {@code null} — это и есть причина, по которой
     * колонка объявлена {@code null}-able.
     */
    @Column(name = "assigned_at")
    private Instant assignedAt;

    /**
     * Срок исполнения участника. При генерации маршрута не рассчитывается: в схему поле
     * заложено под {@code due_offset} доп. согласующих, а этот механизм (PHASE-10) не реализован.
     */
    @Column(name = "due_at")
    private Instant dueAt;

    /**
     * Время решения участника. Поле объявлено моделью, но код приложения в него не пишет: переход
     * {@code Decide} (V20) выполняет действия {@code RecordDecision, RecordComment,
     * EvaluateAggregation}, и момент решения фиксируется только в
     * {@link Decision#getRecordedAt()}. Агрегация это поле тоже не читает — источником истины
     * остаётся {@link Decision} (см. ограничение у {@link #decision}).
     */
    @Column(name = "decided_at")
    private Instant decidedAt;

    /** Время создания задачи при генерации маршрута. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Время последнего изменения задачи (клонирование в новую итерацию, назначение задач). */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Дополнительные согласующие, подчинённые этому участнику. Коллекция объявлена, но не
     * заполняется: при генерации маршрута {@code AdditionalApprover} не создаются (PHASE-10), а
     * слот {@code ADDITIONAL_APPROVER} даёт обычную строку {@code Participant}.
     */
    @OneToMany(mappedBy = "participant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @lombok.Builder.Default
    private List<AdditionalApprover> additionalApprovers = new ArrayList<>();
}
