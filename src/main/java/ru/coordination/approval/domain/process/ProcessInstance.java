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

    @Column(name = "entity_type", length = 100, nullable = false)
    private String entityType;

    @Column(name = "entity_subtype", length = 100)
    private String entitySubtype;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

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

    @Column(name = "config_version", nullable = false)
    private Integer configVersion;

    /** Валидируется через {@code StatusRegistry} (entityType=PROCESS), не через CHECK. */
    @Column(name = "status", length = 100, nullable = false)
    private String status;

    @Column(name = "initiator_id", nullable = false)
    private UUID initiatorId;

    @Column(name = "responsible_user_id")
    private UUID responsibleUserId;

    @Column(name = "responsible_resolved_role_ref")
    private UUID responsibleResolvedRoleRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "auto_archive_scheduled_at")
    private Instant autoArchiveScheduledAt;

    /** Оптимистичная блокировка (03_domain_model.md §7.2). */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @OneToMany(mappedBy = "process", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIdx ASC")
    @lombok.Builder.Default
    private List<StageInstance> stages = new ArrayList<>();
}
