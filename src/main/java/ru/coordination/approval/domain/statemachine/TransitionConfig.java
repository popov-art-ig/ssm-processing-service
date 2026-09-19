package ru.coordination.approval.domain.statemachine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.coordination.approval.domain.common.BaseEntity;

/**
 * Переход между состояниями. Guards/actions — коды, резолвящиеся {@code TransitionEngine}
 * через {@code GuardRegistry}/{@code ActionRegistry} (ADR-028, 10_architecture.md §7.2.1,
 * §7.2.5, §7.2.6). Алгоритм отбора и исполнения — 04_state_machines.md §3.3: поиск по
 * (entityType, fromState, trigger, processType) → сортировка по priority → guards → actions.
 *
 * См. 03_domain_model.md §8.4, 08_db_schema.md §15.3.
 */
@Entity
@Table(name = "transition_config")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class TransitionConfig extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false)
    private StateMachineConfig config;

    @Column(name = "code", length = 100, nullable = false)
    private String code;

    @Column(name = "from_state", length = 100, nullable = false)
    private String fromState;

    @Column(name = "to_state", length = 100, nullable = false)
    private String toState;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger", length = 50, nullable = false)
    private TriggerType trigger;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "guards", columnDefinition = "text[]", nullable = false)
    @lombok.Builder.Default
    private List<String> guards = List.of();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "actions", columnDefinition = "text[]", nullable = false)
    @lombok.Builder.Default
    private List<String> actions = List.of();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "emits", columnDefinition = "text[]", nullable = false)
    @lombok.Builder.Default
    private List<String> emits = List.of();

    @Column(name = "priority", nullable = false)
    @lombok.Builder.Default
    private Integer priority = 0;

    @Column(name = "is_active", nullable = false)
    @lombok.Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
