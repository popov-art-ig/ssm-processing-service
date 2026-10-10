package ru.coordination.approval.domain.registry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Справочник всех возможных статусов для сущностей (RegistryAggregate).
 * С ADR-028 — единственный валидатор {@code status}-полей всех агрегатов
 * (ранее статус дублировался в {@code ssm_state_machine_context.state}).
 *
 * См. 03_domain_model.md §5.2, 08_db_schema.md §5.1.
 */
@Entity
@Table(name = "status_registry")
@IdClass(StatusRegistryId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusRegistry {

    /**
     * Код статуса. Уникален в рамках {@link #entityType} — ключ составной
     * ({@link StatusRegistryId}), см. миграцию {@code V27__status_registry_composite_key.sql}.
     */
    @Id
    @Column(name = "code", length = 100, nullable = false)
    private String code;

    /**
     * Тип сущности-владельца статуса ({@link ru.coordination.approval.domain.common.EntityType}
     * как {@code String} — так задано схемой). Вторая часть составного ключа
     * ({@link StatusRegistryId}): один и тот же код осмысленно повторяется у разных агрегатов
     * ({@code Active} у STAGE и REMARK, {@code InProgress} у PROCESS и REMARK).
     */
    @Id
    @Column(name = "entity_type", length = 50, nullable = false)
    private String entityType;

    /** Название статуса для UI (например «На доработке» для {@code OnRework}). */
    @Column(name = "display_name", length = 255, nullable = false)
    private String displayName;

    /** Пояснение к статусу для администратора; в логике переходов не участвует. */
    @Column(name = "description")
    private String description;

    /**
     * Признак конечного статуса. В {@code TransitionEngine} не проверяется (переход валидируется
     * только по наличию {@code toState} в реестре) — используется как справочная информация и
     * дублирует {@code state_config.is_terminal} для конкретной карты.
     */
    @Column(name = "is_terminal", nullable = false)
    @Builder.Default
    private boolean terminal = false;

    /** Категория статуса для группировки в UI; заявлена свободной строкой и не валидируется. */
    @Column(name = "category", length = 50)
    private String category;

    /**
     * Дополнительные атрибуты статуса (JSON). Миграции заполняют его пустым объектом, и код
     * реестра к {@code metadata} не обращается — поле оставлено как точка расширения.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    /**
     * Мягкое отключение статуса. В отличие от реестров guard/action, здесь {@code is_active}
     * нигде не читается: {@code TransitionEngine.validateTargetStatus} проверяет только факт
     * наличия строки {@code (code, entityType)}, поэтому деактивированный статус всё равно
     * остаётся допустимой целью перехода.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Момент добавления статуса в справочник. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Момент последнего изменения записи справочника. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
