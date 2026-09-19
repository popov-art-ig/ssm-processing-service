package ru.coordination.approval.domain.statemachine;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
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
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.common.ProcessType;

/**
 * Конфигурация карты переходов — корень StateMachineAggregate. Читается {@code ModelFactory}
 * и исполняется собственным {@code TransitionEngine} (ADR-028, заменил Spring State Machine /
 * ADR-011) по алгоритму 04_state_machines.md §3.3.
 *
 * <p>{@code version} — бизнес-номер версии конфигурации (Publish создаёт новую строку с
 * {@code version = предыдущая + 1}), не путать с {@link #getVersionLock()} — техническим полем
 * оптимистичной блокировки записи ({@code version_lock} в БД).
 *
 * См. 03_domain_model.md §8.2, 08_db_schema.md §15.1.
 */
@Entity
@Table(name = "state_machine_config")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class StateMachineConfig extends BaseEntity {

    @Column(name = "version", nullable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 50, nullable = false)
    private EntityType entityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "process_type", length = 50)
    private ProcessType processType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private LifecycleStatus status;

    @Column(name = "active_process_count", nullable = false)
    @lombok.Builder.Default
    private Integer activeProcessCount = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Техническое поле оптимистичной блокировки (JPA {@code @Version}), отдельное от бизнес-{@link #version}. */
    @Version
    @Column(name = "version_lock", nullable = false)
    private Integer versionLock;

    @OneToMany(mappedBy = "config", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @lombok.Builder.Default
    private List<StateConfig> states = new ArrayList<>();

    @OneToMany(mappedBy = "config", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @lombok.Builder.Default
    private List<TransitionConfig> transitions = new ArrayList<>();
}
