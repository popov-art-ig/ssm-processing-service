package ru.coordination.approval.domain.statemachine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.coordination.approval.domain.common.BaseEntity;

/**
 * Состояние в карте переходов. См. 03_domain_model.md §8.3, 08_db_schema.md §15.2.
 */
@Entity
@Table(name = "state_config")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class StateConfig extends BaseEntity {

    /**
     * Карта-владелец состояния. Связь {@code LAZY}: модель строится запросом по {@code config_id}
     * ({@code StateConfigRepository}), а не обходом коллекции {@code StateMachineConfig.states}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false)
    private StateMachineConfig config;

    /** Код состояния — то, что попадает в {@code from_state}/{@code to_state} переходов и в {@code status} сущности. */
    @Column(name = "code", length = 100, nullable = false)
    private String code;

    @Column(name = "display_name", length = 255, nullable = false)
    private String displayName;

    /**
     * Начальное состояние карты. Ровно одно на карту — это проверяет
     * {@code TransitionMapValidator} ({@code INITIAL_STATE_MISSING},
     * {@code MULTIPLE_INITIAL_STATES}, {@code 05_guards_actions_registry.md} §7.2); движок
     * начальное состояние не ищет, его передаёт вызывающий код как {@code fromState}.
     */
    @Column(name = "is_initial", nullable = false)
    @lombok.Builder.Default
    private boolean initial = false;

    /**
     * Терминальное состояние. Движком не проверяется: карта не запрещает объявить переход из
     * терминального состояния — за корректность карты отвечает валидация, а не исполнение.
     */
    @Column(name = "is_terminal", nullable = false)
    @lombok.Builder.Default
    private boolean terminal = false;

    /**
     * Произвольные метаданные состояния (jsonb). Схема их не использует — задел под UI-редактор
     * карты ({@code 04_state_machines.md} §14.5); движок читает только {@code code}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    @lombok.Builder.Default
    private Map<String, Object> metadata = Map.of();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
