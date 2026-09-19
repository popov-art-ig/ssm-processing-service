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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_slot_id")
    private SlotTemplate parentSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_template_id")
    private StageTemplate stageTemplate;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot_type", length = 50, nullable = false)
    private SlotType slotType;

    @Column(name = "order_idx", nullable = false)
    private Integer orderIdx;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "acceptable_roles", columnDefinition = "uuid[]", nullable = false)
    @lombok.Builder.Default
    private List<UUID> acceptableRoles = new ArrayList<>();

    @Column(name = "required", nullable = false)
    @lombok.Builder.Default
    private boolean required = true;

    @Column(name = "is_user_editable", nullable = false)
    @lombok.Builder.Default
    private boolean userEditable = true;

    @Column(name = "is_organization_editable", nullable = false)
    @lombok.Builder.Default
    private boolean organizationEditable = false;

    @Column(name = "is_deletable", nullable = false)
    @lombok.Builder.Default
    private boolean deletable = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "due_offset", length = 10)
    private DueOffset dueOffset;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "parentSlot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @lombok.Builder.Default
    private List<SlotTemplate> children = new ArrayList<>();
}
