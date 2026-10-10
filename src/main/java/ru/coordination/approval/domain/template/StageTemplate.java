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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.coordination.approval.domain.common.BaseEntity;
import ru.coordination.approval.domain.common.DecisionMode;
import ru.coordination.approval.domain.common.ExecutionOrder;
import ru.coordination.approval.domain.common.StageType;

/**
 * Описание одного этапа в шаблоне маршрута.
 * См. 03_domain_model.md §6.4, 08_db_schema.md §6.3.
 *
 * <p>Шаблон этапа — источник значений, которые копируются в этап процесса при старте
 * ({@link ru.coordination.approval.service.RouteGenerationService}): порядок, тип, сроки, режим
 * агрегации, порядок работы участников, обязательность и допустимые цели возврата. Дальнейшие
 * правки шаблона на уже созданный маршрут не влияют.
 */
@Entity
@Table(name = "stage_template")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class StageTemplate extends BaseEntity {

    /** Версия шаблона-владельца (ADR-023: этапы принадлежат конкретной версии, не «шаблону вообще»). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    /**
     * Порядок этапа в маршруте (нумерация с 1, уникальна в рамках шаблона). Это же значение
     * используется как адрес этапа в {@link #allowedReturnStages}.
     */
    @Column(name = "order_idx", nullable = false)
    private Integer orderIdx;

    /** Опциональное название этапа для UI. */
    @Column(name = "name", length = 255)
    private String name;

    /** Опциональное описание этапа для UI. */
    @Column(name = "description")
    private String description;

    /**
     * {@link StageType} этапа. Обязателен структурно ({@code ValidateTemplateStructureAction}
     * проверяет лишь непустоту набора этапов), а семантических различий между значениями в
     * guards/actions нет.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "stage_type", length = 50, nullable = false)
    private StageType stageType;

    /**
     * Срок этапа. Единица измерения принята как <b>дни</b>:
     * {@link ru.coordination.approval.service.action.CalcStageDueAtAction} и
     * {@link ru.coordination.approval.service.RouteGenerationService} прибавляют это число через
     * {@code ChronoUnit.DAYS} (поле БД хранит просто целое, без указания единицы).
     */
    @Column(name = "duration")
    private Integer duration;

    /**
     * Режим агрегации решений ({@link DecisionMode}). Обязателен для {@code STANDARD} и запрещён
     * для {@code UNIFIED} — это проверяет {@code ValidateTemplateStructureAction} (PHASE-14).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "decision_mode", length = 50)
    private DecisionMode decisionMode;

    /** Порядок работы участников этапа ({@link ExecutionOrder}); те же ограничения по типу процесса. */
    @Enumerated(EnumType.STRING)
    @Column(name = "execution_order", length = 50)
    private ExecutionOrder executionOrder;

    /**
     * Обязательность этапа: переносится в {@code StageInstance.mandatory} при генерации маршрута и
     * проверяется {@code AllMandatorySlotsFilledGuard} (у каждого обязательного этапа в текущей
     * итерации должен быть хотя бы один участник) — в упрощённой per-stage реализации, а не
     * per-slot.
     */
    @Column(name = "is_mandatory", nullable = false)
    @lombok.Builder.Default
    private boolean mandatory = false;

    /**
     * Обязательность порядка. Признак объявлен в модели и схеме ({@code 03_domain_model.md} §6.4,
     * «Обязательность порядка»), но ни спецификация guards/actions, ни код его не читают: он
     * только переносится из шаблона в {@code StageInstance} при генерации маршрута.
     */
    @Column(name = "is_order_mandatory", nullable = false)
    @lombok.Builder.Default
    private boolean orderMandatory = false;

    /** Список orderIdx этапов, на которые допустим возврат. null/{} — только текущий этап. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_return_stages", columnDefinition = "integer[]")
    private List<Integer> allowedReturnStages;

    /** Время создания строки этапа. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Слоты участников этапа по порядку {@code orderIdx}. Слоты типа
     * {@link SlotType#ADDITIONAL_APPROVER} вложены в слот-родитель и в этом списке не появляются —
     * сюда попадают только {@link SlotType#ACTOR}.
     */
    @OneToMany(mappedBy = "stageTemplate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIdx ASC")
    @lombok.Builder.Default
    private List<SlotTemplate> actorSlots = new ArrayList<>();
}
