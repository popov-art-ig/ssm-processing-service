package ru.coordination.approval.domain.registry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.coordination.approval.domain.common.RegistryScope;

/**
 * Запись реестра action-обработчиков, используемых {@code TransitionEngine} (ADR-028).
 * Резолвится по коду через {@code ActionRegistry} (10_architecture.md §7.2.6).
 *
 * <p>Структура зеркальна {@link GuardRegistryEntry} ({@code 05_guards_actions_registry.md} §3.1,
 * {@code 08_db_schema.md} §16.2) — отличается только тип резолвимого bean-а: action, в отличие от
 * guard, выполняет побочное действие и не возвращает результат. Незарегистрированный код
 * отклоняется валидатором карты с {@code ACTION_NOT_FOUND} (§7).
 *
 * См. 08_db_schema.md §16.2, 05_guards_actions_registry.md.
 */
@Entity
@Table(name = "action_registry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActionRegistryEntry {

    /**
     * Код действия — первичный ключ и значение из {@code transition_config.actions[]}
     * (например {@code RecordDecision} или {@code AssignStageTasks}); выполняется в порядке
     * перечисления в массиве.
     */
    @Id
    @Column(name = "code", length = 100, nullable = false)
    private String code;

    /** Человекочитаемое название для административного UI. */
    @Column(name = "display_name", length = 255, nullable = false)
    private String displayName;

    /** Описание побочного эффекта действия для администратора. */
    @Column(name = "description", nullable = false)
    private String description;

    /** Полное имя Spring bean-а / класса, реализующего action. */
    @Column(name = "handler", length = 255, nullable = false)
    private String handler;

    /**
     * JSON-схема параметров действия. Миграции её не заполняют (поле {@code null}), поэтому
     * валидация параметров перехода фактически не выполняется — см.
     * {@code 05_guards_actions_registry.md} §7.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params_schema")
    private Map<String, Object> paramsSchema;

    /**
     * {@link RegistryScope#GLOBAL} — встроенное действие, {@link RegistryScope#CUSTOM} —
     * пользовательское ({@code 05_guards_actions_registry.md} §3.2).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", length = 50, nullable = false)
    private RegistryScope scope;

    /**
     * Категории действия для группировки в UI ({@code LIFECYCLE}, {@code DATA},
     * {@code NOTIFICATION}, {@code TASK} и др. — {@code 05_guards_actions_registry.md} §3.3).
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "categories", columnDefinition = "text[]", nullable = false)
    @Builder.Default
    private List<String> categories = List.of();

    /**
     * Типы сущностей ({@link ru.coordination.approval.domain.common.EntityType}), к которым
     * действие применимо; проверяется валидатором карты ({@code APPLICABILITY_MISMATCH}, §7).
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "applicable_entities", columnDefinition = "text[]", nullable = false)
    @Builder.Default
    private List<String> applicableEntities = List.of();

    /**
     * Мягкое отключение записи: неактивный код не резолвится в bean, и переход, ссылающийся на
     * него, отклоняется с {@code UnknownActionException} (проверка на каждый вызов, не только при
     * валидации карты — см. {@link ru.coordination.approval.engine.registry.ActionRegistry}).
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Момент регистрации записи. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Момент последнего изменения записи. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
