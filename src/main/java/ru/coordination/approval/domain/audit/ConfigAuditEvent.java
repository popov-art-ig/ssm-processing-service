package ru.coordination.approval.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.coordination.approval.domain.common.BaseEntity;

/**
 * Аудит изменений конфигурации state machine (публикация/депрекация версий).
 * {@code configId} — ссылка на {@code state_machine_config.id}; хранится как «голый» id
 * (AuditAggregate не должен зависеть от Java-класса StateMachineAggregate).
 *
 * См. 03_domain_model.md §9.2, 08_db_schema.md §21.2.
 */
@Entity
@Table(name = "config_audit_event")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ConfigAuditEvent extends BaseEntity {

    /**
     * Идентификатор изменённой версии карты ({@code state_machine_config.id}). Хранится «голым»
     * id, потому что между {@code configId} и {@code (entityType, version)} нет однозначного
     * соответствия без обращения к другой таблице.
     */
    @Column(name = "config_id", nullable = false)
    private UUID configId;

    /**
     * Номер версии карты на момент события — денормализация
     * {@code state_machine_config.version}: версия неизменяема, поэтому расхождения со временем
     * не возникает, а читателю аудита не нужен join.
     */
    @Column(name = "config_version", nullable = false)
    private Integer configVersion;

    /** Тип действия над конфигурацией — см. {@link ConfigAuditAction}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 50, nullable = false)
    private ConfigAuditAction action;

    /** Пользователь-администратор, выполнивший действие; системные изменения не предусмотрены. */
    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    /**
     * Что изменилось (JSON-дифф). Ни одна миграция его не заполняет, и писателя у сущности пока
     * нет: жизненный цикл шаблонов/карт выполняется через {@code TransitionEngine} и
     * фиксируется в {@link AuditEvent}, а не здесь (PHASE-14).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diff", nullable = false)
    @lombok.Builder.Default
    private Map<String, Object> diff = Map.of();

    /** Комментарий администратора к изменению. */
    @Column(name = "comment")
    private String comment;

    /** Время события; ключ партиционирования таблицы по месяцам ({@code 08_db_schema.md} §21.2). */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
