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

    /**
     * Бизнес-номер версии: {@code Publish} создаёт новую строку с {@code version + 1}, а не
     * меняет существующую. Уникален в рамках {@code (entity_type, process_type)} — индекс
     * {@code ux_state_machine_config_version}.
     */
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * Тип сущности, к которой применяется карта. Связывает карту с реестром статусов
     * ({@code status_registry.entity_type}) и с реестрами guard/action
     * ({@code applicable_entities}); {@code TransitionEngine} сверяет его с типом переданной
     * сущности, чтобы карта процесса не была применена к этапу.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 50, nullable = false)
    private EntityType entityType;

    /**
     * Тип процесса для карт уровня процесса ({@code PROCESS}); {@code null} у карт, не зависящих
     * от типа процесса (например, {@code TEMPLATE} в V25). Колонка nullable, поэтому код чтения
     * обязан проверять {@code null} перед {@code name()} — прямой вызов давал NPE и 500 на
     * чтении карт шаблонов (см. {@code StateMachineConfigController.toDto}).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "process_type", length = 50)
    private ProcessType processType;

    /**
     * Жизненный цикл версии: {@code Draft → Published → Deprecated → Archived}
     * ({@code 04_state_machines.md} §14.1). Исполняется движком и проверяется на старте только
     * {@code PUBLISHED} — черновик не должен влиять на работу сервиса.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private LifecycleStatus status;

    /**
     * Число процессов, стартовавших по этой версии: версию нельзя удалить, пока счётчик не 0
     * (Fork &amp; Drain, ADR-003; {@code 04_state_machines.md} §3.4) — иначе работающие процессы
     * остались бы без карты переходов.
     */
    @Column(name = "active_process_count", nullable = false)
    @lombok.Builder.Default
    private Integer activeProcessCount = 0;

    /** Время создания версии карты; техническое поле аудита, кодом не читается. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Автор создания версии карты; техническое поле аудита, кодом не читается. */
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    /** Время публикации версии — часть аудита конфига ({@code 04_state_machines.md} §14.4). */
    @Column(name = "published_at")
    private Instant publishedAt;

    /** Публикатор версии — парное к {@code published_at} поле аудита конфига ({@code 04_state_machines.md} §14.4). */
    @Column(name = "published_by")
    private UUID publishedBy;

    /** Время последнего изменения версии (в том числе смены статуса жизненного цикла). */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Техническое поле оптимистичной блокировки (JPA {@code @Version}), отдельное от бизнес-{@link #version}. */
    @Version
    @Column(name = "version_lock", nullable = false)
    private Integer versionLock;

    /**
     * Состояния карты. Загружаются отдельным запросом ({@code StateConfigRepository}), а не
     * через {@code join fetch} вместе с {@code transitions}: Hibernate не умеет тянуть две
     * {@code List}-коллекции одной карты за раз ({@code MultipleBagFetchException}).
     */
    @OneToMany(mappedBy = "config", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @lombok.Builder.Default
    private List<StateConfig> states = new ArrayList<>();

    /**
     * Переходы карты — второй «мешок» коллекции ({@code TransitionConfigRepository}); причины
     * раздельной загрузки те же, что у {@link #states}.
     */
    @OneToMany(mappedBy = "config", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @lombok.Builder.Default
    private List<TransitionConfig> transitions = new ArrayList<>();
}
