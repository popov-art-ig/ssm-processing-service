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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false)
    private StateMachineConfig config;

    @Column(name = "code", length = 100, nullable = false)
    private String code;

    @Column(name = "display_name", length = 255, nullable = false)
    private String displayName;

    @Column(name = "is_initial", nullable = false)
    @lombok.Builder.Default
    private boolean initial = false;

    @Column(name = "is_terminal", nullable = false)
    @lombok.Builder.Default
    private boolean terminal = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    @lombok.Builder.Default
    private Map<String, Object> metadata = Map.of();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
