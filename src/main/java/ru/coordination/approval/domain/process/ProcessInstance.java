package ru.coordination.approval.domain.process;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import ru.coordination.approval.domain.common.BaseEntity;
import ru.coordination.approval.domain.common.ProcessType;

/**
 * Экземпляр процесса согласования — корень ProcessAggregate.
 *
 * <p>С ADR-028 поле {@code status} — единственное хранимое представление текущего
 * состояния процесса (ранее дублировалось в {@code ssm_state_machine_context.state}).
 * Переходы исполняет {@code TransitionEngine} (10_architecture.md §7.2.1), читая карту
 * из {@code StateMachineConfig} (entityType=PROCESS) и валидируя {@code status} через
 * {@code StatusRegistry} (ADR-015).
 *
 * См. 03_domain_model.md §7.2, 08_db_schema.md §7.1.
 */
@Entity
@Table(name = "process_instance")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ProcessInstance extends BaseEntity {

    /**
     * Тип сущности, которую согласует процесс (дискриминатор внешней сущности из
     * {@code EntityAdapter}, а не {@link ru.coordination.approval.domain.common.EntityType}).
     * Вместе с {@link #entityId} образует ссылку на предмет согласования; неизменяем после старта
     * ({@code 03_domain_model.md} §10.1).
     */
    @Column(name = "entity_type", length = 100, nullable = false)
    private String entityType;

    /** Подтип внешней сущности; используется правилами применимости шаблона при подборе. */
    @Column(name = "entity_subtype", length = 100)
    private String entitySubtype;

    /** Идентификатор внешней сущности (например документа) в её собственном сервисе. */
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    /**
     * Агрегат документа, к которому относится процесс. Заполняется для ревизий: по этому полю
     * вместе с {@link #revisionLabel} БД запрещает дубли (уникальный индекс
     * {@code ux_process_revision}, {@code 08_db_schema.md} §7.1).
     */
    @Column(name = "document_aggregate_id")
    private UUID documentAggregateId;

    /** Свободная строка (Изм. 1, Доп. 2, ТР. 3, Зам. 4) — формат не валидируется модулем. */
    @Column(name = "revision_label", length = 100)
    private String revisionLabel;

    /** Ссылка на предыдущий процесс того же документного агрегата. Хранится как «голый» id. */
    @Column(name = "parent_process_id")
    private UUID parentProcessId;

    /** Ссылка на конкретную версию {@code Template} (ADR-023 — версия зафиксирована самой ссылкой). */
    @Column(name = "template_ref", nullable = false)
    private UUID templateRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "process_type", length = 50, nullable = false)
    private ProcessType processType;

    /**
     * Версия карты {@code state_machine_config}, зафиксированная на момент старта процесса
     * (Snapshot-on-Start, ADR-003): по ней {@code ModelFactory} строит модель переходов, поэтому
     * последующая публикация новой карты уже начатые процессы не затрагивает. Значение неизменяемо
     * после старта. Сейчас генератор маршрута проставляет константу {@code 1}.
     */
    @Column(name = "config_version", nullable = false)
    private Integer configVersion;

    /** Валидируется через {@code StatusRegistry} (entityType=PROCESS), не через CHECK. */
    @Column(name = "status", length = 100, nullable = false)
    private String status;

    /** Инициатор процесса: по нему проверяются права на запуск, отзыв и возобновление. */
    @Column(name = "initiator_id", nullable = false)
    private UUID initiatorId;

    /** Ответственный (только {@code UNIFIED}); у {@code STANDARD} остаётся {@code null}. */
    @Column(name = "responsible_user_id")
    private UUID responsibleUserId;

    /**
     * Идентификатор роли, по которой был разрешён ответственный (ADR-010). Модуль его только
     * хранит — обратное разрешение делает внешний сервис ролей.
     */
    @Column(name = "responsible_resolved_role_ref")
    private UUID responsibleResolvedRoleRef;

    /** Время создания процесса; в этот момент он ещё в начальном статусе и не запущен. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Время старта процесса — записывается action {@code SetStartedAt} при переходе {@code StartProcess}. */
    @Column(name = "started_at")
    private Instant startedAt;

    /** Время завершения согласования — записывается action {@code CompleteProcess}. */
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Время помещения процесса в архив. Автоархивация не реализована (нет джобы и действия
     * {@code A-P-007 ScheduleAutoArchive} в карте переходов), поэтому поле заполняется только
     * внешними вызовами, если такие появятся.
     */
    @Column(name = "archived_at")
    private Instant archivedAt;

    /**
     * Момент, когда процесс должен быть автоматически архивирован. По спецификации
     * {@code A-P-003} равен {@code completedAt + 180 дней}, однако {@code CompleteProcessAction}
     * это поле не заполняет — планировщик автоархивации не реализован.
     */
    @Column(name = "auto_archive_scheduled_at")
    private Instant autoArchiveScheduledAt;

    /** Оптимистичная блокировка (03_domain_model.md §7.2). */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * Этапы маршрута — «снимок» шаблона на момент старта (Snapshot-on-Start, ADR-003). Новых
     * этапов после старта не появляется: возобновление работы меняет статусы существующих строк
     * и создаёт итерации, а не этапы.
     */
    @OneToMany(mappedBy = "process", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIdx ASC")
    @lombok.Builder.Default
    private List<StageInstance> stages = new ArrayList<>();
}
