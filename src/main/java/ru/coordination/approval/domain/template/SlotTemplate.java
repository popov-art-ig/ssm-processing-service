package ru.coordination.approval.domain.template;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.coordination.approval.domain.common.BaseEntity;
import ru.coordination.approval.domain.common.DueOffset;

/**
 * Унифицированный слот участника в шаблоне (ACTOR — основной участник этапа,
 * ADDITIONAL_APPROVER — дополнительный согласующий, вложенный в другой слот).
 *
 * <p>Инвариант (check-констрейнт в БД, §6.4 08_db_schema.md):
 * {@code (slotType=ACTOR AND parentSlot=null AND stageTemplate!=null)
 * OR (slotType=ADDITIONAL_APPROVER AND parentSlot!=null AND stageTemplate=null)}.
 * На уровне приложения инвариант следует проверять в сервисном слое (не в этой сущности).
 *
 * См. 03_domain_model.md §6.5.
 */
@Entity
@Table(name = "slot_template")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SlotTemplate extends BaseEntity {

    /**
     * Родительский слот для {@link SlotType#ADDITIONAL_APPROVER} — задаёт иерархию доп.
     * согласующих (вложенность не ограничена). У {@link SlotType#ACTOR} всегда {@code null}:
     * это половина check-констрейнта из Javadoc класса, вторая — {@link #stageTemplate}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_slot_id")
    private SlotTemplate parentSlot;

    /** Этап шаблона-владелец; заполнен только у {@link SlotType#ACTOR} (см. инвариант класса). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_template_id")
    private StageTemplate stageTemplate;

    /** Тип слота — от него зависит и инвариант связей, и роль создаваемого участника. */
    @Enumerated(EnumType.STRING)
    @Column(name = "slot_type", length = 50, nullable = false)
    private SlotType slotType;

    /**
     * Порядок слота внутри этапа (для доп. согласующих — среди детей одного родителя).
     * Переносится в {@code Participant.orderIdx}, то есть определяет очередь выдачи задач при
     * {@code executionOrder = SEQUENTIAL} (ADR-022).
     */
    @Column(name = "order_idx", nullable = false)
    private Integer orderIdx;

    /**
     * Конкретный назначенный пользователь — второй по приоритету источник после
     * {@link #acceptableRoles} ({@code RouteGenerationService.resolveSlot}: сначала пробуется
     * роль, и только при пустом списке ролей берётся фиксированный пользователь).
     */
    @Column(name = "user_id")
    private UUID userId;

    /**
     * Организация слота. В резолвинг участников не передаётся:
     * {@code RouteGenerationService.resolveSlot} опирается только на {@code acceptableRoles} и
     * {@code userId}, поэтому ограничение по организации фактически не применяется.
     */
    @Column(name = "organization_id")
    private UUID organizationId;

    /**
     * Допустимые роли-идентификаторы: из первой роли слота
     * {@link ru.coordination.approval.adapter.RoleResolverAdapter} получает список конкретных
     * пользователей; если ролей несколько, остальные сейчас игнорируются
     * ({@code RouteGenerationService.resolveSlot}).
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "acceptable_roles", columnDefinition = "uuid[]", nullable = false)
    @lombok.Builder.Default
    private List<UUID> acceptableRoles = new ArrayList<>();

    /**
     * Обязательность слота: незаполненный обязательный слот даёт
     * {@code RequiredSlotNotResolvedException} при генерации маршрута, необязательный просто не
     * создаёт участника. Проверка буквальной per-slot обязательности на этапе guard'а
     * {@code AllMandatorySlotsFilledGuard} отсутствует (там проверяется лишь наличие участников у
     * обязательного этапа).
     */
    @Column(name = "required", nullable = false)
    @lombok.Builder.Default
    private boolean required = true;

    /**
     * Разрешено ли менять пользователя слота в UI. Смысл парный с
     * {@link #organizationEditable}, инвариант «{@code organizationEditable = true} влечёт
     * {@code userEditable = true}» закреплён check-констрейнтом БД и проверкой
     * {@code ValidateTemplateStructureAction}.
     */
    @Column(name = "is_user_editable", nullable = false)
    @lombok.Builder.Default
    private boolean userEditable = true;

    /** Разрешено ли менять организацию слота; см. {@link #userEditable}. */
    @Column(name = "is_organization_editable", nullable = false)
    @lombok.Builder.Default
    private boolean organizationEditable = false;

    /** Разрешено ли удалять слот из маршрута в UI. */
    @Column(name = "is_deletable", nullable = false)
    @lombok.Builder.Default
    private boolean deletable = false;

    /**
     * Смещение срока доп. согласующего относительно срока основного участника
     * ({@link DueOffset}, {@code 01_glossary.md} §5.8). В {@code RouteGenerationService} не
     * применяется: сроки доп. согласующих при генерации маршрута не рассчитываются.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "due_offset", length = 10)
    private DueOffset dueOffset;

    /** Время создания строки слота. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Вложенные доп. согласующие. При клонировании/форке шаблона дерево слотов копируется
     * целиком ({@code TemplateService.applySlots}); в маршрут процесса такие слоты пока не
     * переносятся — {@code RouteGenerationService} обходит только {@code actorSlots} верхнего
     * уровня.
     */
    @OneToMany(mappedBy = "parentSlot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @lombok.Builder.Default
    private List<SlotTemplate> children = new ArrayList<>();
}
