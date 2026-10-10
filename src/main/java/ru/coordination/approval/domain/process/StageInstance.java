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
 * Этап в маршруте процесса. См. 03_domain_model.md §7.3, 08_db_schema.md §8.1.
 *
 * <p>Этап — «снимок» строки {@code stage_template} на момент старта процесса: значения
 * {@code stageType}, {@code duration}, {@code decisionMode}, {@code executionOrder},
 * {@code mandatory}, {@code allowedReturnStages} скопированы и дальше не синхронизируются с
 * шаблоном. Собственного состава участников у этапа нет — участники принадлежат конкретной
 * {@link StageIteration} (итерации), потому что при возврате на доработку этап проходит круг
 * заново (ADR-009).
 */
@Entity
@Table(name = "stage_instance")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class StageInstance extends BaseEntity {

    /** Процесс-владелец; этап существует только внутри своего процесса. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id", nullable = false)
    private ProcessInstance process;

    /**
     * Порядок этапа в маршруте. Используется как «адрес» этапа при возврате и при активации
     * следующего этапа; уникален в рамках процесса (индекс {@code ux_stage_instance_order}).
     */
    @Column(name = "order_idx", nullable = false)
    private Integer orderIdx;

    /**
     * Порядок этапа «из шаблона» ({@code 03_domain_model.md} §7.3) — копия {@code orderIdx} на
     * момент генерации маршрута. Задуман как исходная позиция на случай перестановки этапов, но
     * ни один сервис или guard это поле не читает.
     */
    @Column(name = "original_order_idx", nullable = false)
    private Integer originalOrderIdx;

    /** Название этапа (из шаблона) для отображения в UI. */
    @Column(name = "name", length = 255)
    private String name;

    /** Описание этапа (из шаблона) для отображения в UI. */
    @Column(name = "description")
    private String description;

    /** Тип этапа; поведение guards/actions от него не зависит (см. {@link StageType}). */
    @Enumerated(EnumType.STRING)
    @Column(name = "stage_type", length = 50, nullable = false)
    private StageType stageType;

    /** Срок этапа в днях (копия из шаблона); {@code dueAt} рассчитывается от него. */
    @Column(name = "duration", nullable = false)
    private Integer duration;

    /**
     * Режим агрегации решений этапа (копия из шаблона). Если шаблон поле не задал,
     * {@code RouteGenerationService} подставляет {@link DecisionMode#AND}; guards агрегации
     * дополнительно страхуют {@code null} тем же значением по умолчанию.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "decision_mode", length = 50)
    private DecisionMode decisionMode;

    /**
     * Порядок работы участников (копия из шаблона): {@code SEQUENTIAL} выдаёт задачи по одному,
     * {@code PARALLEL} и {@code null} — всем сразу (см. {@link ExecutionOrder}).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "execution_order", length = 50)
    private ExecutionOrder executionOrder;

    /**
     * Обязательность этапа (копия из шаблона): проверяется {@code AllMandatorySlotsFilledGuard}
     * при запуске процесса.
     */
    @Column(name = "is_mandatory", nullable = false)
    @lombok.Builder.Default
    private boolean mandatory = false;

    /**
     * Признак «обязателен порядок» (копия из шаблона). Никакой логикой не используется — см.
     * {@link ru.coordination.approval.domain.template.StageTemplate#isOrderMandatory()}.
     */
    @Column(name = "is_order_mandatory", nullable = false)
    @lombok.Builder.Default
    private boolean orderMandatory = false;

    /**
     * Допустимые цели возврата — список {@code orderIdx} этапов (не их идентификаторов).
     * Проверяется guard'ом {@code IsTargetStageAllowedGuard} при выборе целевого этапа
     * ({@code 04_state_machines.md} §11); {@code null}/пусто означает «только текущий этап».
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_return_stages", columnDefinition = "integer[]")
    private List<Integer> allowedReturnStages;

    /** Валидируется через {@code StatusRegistry} (entityType=STAGE). */
    @Column(name = "status", length = 100, nullable = false)
    private String status;

    /** Время активации этапа — записывается action {@code SetStageStartedAt}. */
    @Column(name = "started_at")
    private Instant startedAt;

    /**
     * Срок этапа: рассчитывается как «срок предыдущего этапа + {@link #duration} дней» ещё при
     * генерации маршрута ({@code RouteGenerationService} собирает накопительную дату по этапам).
     */
    @Column(name = "due_at")
    private Instant dueAt;

    /** Время закрытия этапа — записывается action {@code SetStageCompletedAt} (PHASE-06). */
    @Column(name = "completed_at")
    private Instant completedAt;

    /** Время создания строки этапа (момент генерации маршрута). */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Круги выполнения этапа по возрастанию {@code iterationIdx}. Новая итерация создаётся при
     * активации этапа, у которого итерации уже есть (ADR-009); guards и actions работают с
     * последней ({@code max(iterationIdx)}) итерацией.
     */
    @OneToMany(mappedBy = "stage", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("iterationIdx ASC")
    @lombok.Builder.Default
    private List<StageIteration> iterations = new ArrayList<>();
}
