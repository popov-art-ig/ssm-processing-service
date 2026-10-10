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

    /**
     * Основной согласующий, к задаче которого прикреплён доп. согласующий. Все доп. согласующие
     * этапа собираются под одним из участников иерархии, а не под этапом напрямую.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    /**
     * Родитель в иерархии; {@code null} у верхнего уровня. Связь задаёт и уровень, и правило
     * check-констрейнта БД: непустой родитель допустим только при
     * {@link AssignedBy#ADDITIONAL_APPROVER}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_additional_approver_id")
    private AdditionalApprover parentAdditionalApprover;

    /**
     * Уровень вложенности: {@code parent.level + 1}, либо 1 при отсутствии родителя
     * ({@code 03_domain_model.md} §7.7). Глубина иерархии не ограничена; значение не
     * рассчитывается кодом — его должно проставлять действие {@code A-H-002 SetLevel}
     * ({@code 05_guards_actions_registry.md} §5.5), которое не реализовано.
     */
    @Column(name = "level", nullable = false)
    @lombok.Builder.Default
    private Integer level = 1;

    /** Пользователь-доп. согласующий; задача принадлежит ему, а не основному участнику. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Организация доп. согласующего. Как и у {@code Participant}, поле нигде не заполняется и не
     * читается: механизм доп. согласующих (PHASE-10) не реализован, сущность пока не создаётся.
     */
    @Column(name = "organization_id")
    private UUID organizationId;

    /**
     * Источник появления доп. согласующего: {@link AssignedBy#TEMPLATE} (слот шаблона),
     * {@link AssignedBy#INITIATOR}, {@link AssignedBy#PARTICIPANT} или
     * {@link AssignedBy#ADDITIONAL_APPROVER} (создан родителем в иерархии, только в этом случае
     * допустим {@link #parentAdditionalApprover}).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_by", length = 50, nullable = false)
    private AssignedBy assignedBy;

    /** Пользователь, назначивший доп. согласующего вручную; при назначении по шаблону пусто. */
    @Column(name = "assigned_by_user_id")
    private UUID assignedByUserId;

    /**
     * Абсолютный срок: рассчитывается из срока основного участника со сдвигом {@link #dueOffset}
     * (действие иерархии {@code A-H-001 CreateChildAdditionalApprover}, не реализованное —
     * PHASE-10).
     */
    @Column(name = "due_at")
    private Instant dueAt;

    /**
     * Смещение срока относительно срока основного участника — «сырьё» для расчёта {@link #dueAt}
     * (значения H0/H3/H8, {@code 01_glossary.md} §5.8). Значение по умолчанию — без сокращения
     * срока.
     */
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

    /** Разрешено ли менять пользователя задачи в UI; поле хранится, но кодом не читается. */
    @Column(name = "is_user_editable", nullable = false)
    @lombok.Builder.Default
    private boolean userEditable = true;

    /** Разрешено ли менять организацию задачи в UI; кодом не читается. */
    @Column(name = "is_organization_editable", nullable = false)
    @lombok.Builder.Default
    private boolean organizationEditable = false;

    /**
     * Разрешено ли удалять задачу в UI. У доп. согласующего по умолчанию {@code true} — в отличие
     * от основного участника: дополнительную задачу можно снять без последствий для маршрута.
     */
    @Column(name = "is_deletable", nullable = false)
    @lombok.Builder.Default
    private boolean deletable = true;

    /** Время выдачи задачи доп. согласующему (в отличие от {@code Participant.assignedAt} — не может быть пустым). */
    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    /** Момент, когда доп. согласующий высказал {@link #recommendation}. */
    @Column(name = "recommended_at")
    private Instant recommendedAt;

    /** Время создания строки. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Время последнего изменения строки. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Дочерние доп. согласующие. Итеративность иерархии ограничена только данными; удаление
     * родителя каскадно удаляет детей ({@code orphanRemoval}).
     */
    @OneToMany(mappedBy = "parentAdditionalApprover", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @lombok.Builder.Default
    private List<AdditionalApprover> children = new ArrayList<>();
}
