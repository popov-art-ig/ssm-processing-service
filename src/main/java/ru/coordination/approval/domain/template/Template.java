package ru.coordination.approval.domain.template;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.common.ProcessType;

/**
 * Многоразовое типовое описание маршрута (корень TemplateAggregate).
 *
 * <p>Версионирование (ADR-023): отдельной сущности {@code TemplateVersion} нет.
 * Публикация новой версии создаёт новую строку {@code Template} со своим id,
 * {@code version = предыдущая + 1} и {@code parentTemplateId}, указывающим на
 * предыдущую версию — та же цепочка, что используется для форка.
 *
 * См. 03_domain_model.md §6.2, 08_db_schema.md §6.1–6.2.
 */
@Entity
@Table(name = "template")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Template extends BaseEntity {

    /** Название шаблона; вместе с {@link #version} образует бизнес-ключ для DRAFT/PUBLISHED. */
    @Column(name = "name", length = 255, nullable = false)
    private String name;

    /**
     * Тип процесса, для которого шаблон применим. Правила применимости
     * ({@link ApplicabilityRule}) дополнительно сужают выбор по типу и подтипу сущности
     * ({@code MatchService}).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "process_type", length = 50, nullable = false)
    private ProcessType processType;

    /**
     * Стадия жизненного цикла ({@link LifecycleStatus}). Переходы выполняет
     * {@code TransitionEngine} по карте {@code TemplateStateMachine} (V25, PHASE-14), а не прямые
     * присваивания: сервис только проверяет предусловие по статусу, иначе движок выбрал бы
     * переход по текущему состоянию (см. README, «Шаблоны: жизненный цикл»).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private LifecycleStatus status;

    /**
     * Номер версии в цепочке {@link #parentTemplateId}. У первой версии — 1; публикация правок
     * опубликованного шаблона создаёт новую строку с {@code version + 1} (ADR-023).
     */
    @Column(name = "version", nullable = false)
    @lombok.Builder.Default
    private Integer version = 1;

    /** Ссылка на предыдущую версию/родителя форка. Cross-aggregate-style ссылка — хранится как «голый» id. */
    @Column(name = "parent_template_id")
    private UUID parentTemplateId;

    /**
     * Разрешает создание ревизий документа ({@code 03_domain_model.md} §6.2): при {@code false} в
     * агрегате документа допускается только один процесс. Важно: это <i>не</i> лимит кругов
     * возврата на доработку — {@code allowedReturnStages} работает независимо от этого признака
     * (ранее признак означал именно лимит повторных запусков маршрута, семантика изменена).
     */
    @Column(name = "process_iteration_enabled", nullable = false)
    @lombok.Builder.Default
    private boolean processIterationEnabled = false;

    /** ADR-023: перенесено из удалённой {@code template_version}. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "responsible_roles", columnDefinition = "uuid[]")
    private List<UUID> responsibleRoles;

    /**
     * Требуется ли согласование маршрута ответственным перед запуском процесса
     * ({@code 01_glossary.md} §4.12). Затрагивает только {@code UNIFIED}; в текущих картах
     * переходов для {@code PROCESS} отдельного перехода согласования маршрута нет.
     */
    @Column(name = "requires_responsible_approval", nullable = false)
    @lombok.Builder.Default
    private boolean requiresResponsibleApproval = false;

    /** Время создания строки версии. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Администратор, создавший версию шаблона. */
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    /** Время последней правки черновика. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Администратор последней правки; {@code null}, если правок после создания не было. */
    @Column(name = "updated_by")
    private UUID updatedBy;

    /**
     * Время публикации версии (проставляется при переходе {@code PublishTemplate}).
     * Опубликованная версия иммутабельна — правки создают новую строку (ADR-023).
     */
    @Column(name = "published_at")
    private Instant publishedAt;

    /** Администратор, опубликовавший версию. */
    @Column(name = "published_by")
    private UUID publishedBy;

    /**
     * Этапы шаблона по порядку {@code orderIdx}. При старте процесса
     * {@link ru.coordination.approval.service.RouteGenerationService} копирует их в
     * {@link ru.coordination.approval.domain.process.StageInstance} — дальше маршрут процесса не
     * зависит от правок шаблона (Snapshot-on-Start, ADR-003).
     */
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIdx ASC")
    @lombok.Builder.Default
    private List<StageTemplate> stages = new ArrayList<>();

    /**
     * Правила применимости по порядку {@code ruleIdx}: в рамках одного шаблона побеждает первое
     * подошедшее правило (его {@code ruleIdx} становится приоритетом шаблона в
     * {@link ru.coordination.approval.service.MatchService}), поэтому порядок значим. Шаблон без
     * правил считается универсальным и участвует в подборе всегда.
     */
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ruleIdx ASC")
    @lombok.Builder.Default
    private List<ApplicabilityRule> applicabilityRules = new ArrayList<>();
}
