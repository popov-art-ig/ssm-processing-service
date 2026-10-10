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
 * Запись реестра guard-условий, используемых {@code TransitionEngine} (ADR-028).
 * Описывает доступные guard-плагины (Spring bean-ы), резолвящиеся по коду через
 * {@code GuardRegistry} (10_architecture.md §7.2.5).
 *
 * <p><b>Роль в data-driven модели (ADR-002, ADR-015).</b> Карта переходов ссылается на условия
 * строками ({@code transition_config.guards[]}), а не классами; эта таблица — единственное место,
 * где строковый код превращается в исполняемый bean
 * ({@link ru.coordination.approval.engine.registry.GuardRegistry#resolve(String)}).
 * Состав полей — {@code 05_guards_actions_registry.md} §3.1, структура таблицы —
 * {@code 08_db_schema.md} §16.1. Коды, не зарегистрированные здесь, отклоняются валидатором карты
 * с {@code GUARD_NOT_FOUND} (§7).
 *
 * См. 08_db_schema.md §16.1, 05_guards_actions_registry.md.
 */
@Entity
@Table(name = "guard_registry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuardRegistryEntry {

    /**
     * Код условия — первичный ключ и то самое значение, которое указывается в
     * {@code transition_config.guards[]} (например {@code AllParticipantsDecided}). Не совпадает
     * с именем bean-а: связь «код → bean» задаётся полем {@link #handler}.
     */
    @Id
    @Column(name = "code", length = 100, nullable = false)
    private String code;

    /** Человекочитаемое название для административного UI (например «Все участники решили»). */
    @Column(name = "display_name", length = 255, nullable = false)
    private String displayName;

    /** Описание контракта условия для администратора; в вычислениях не участвует. */
    @Column(name = "description", nullable = false)
    private String description;

    /** Полное имя Spring bean-а / класса, реализующего guard. */
    @Column(name = "handler", length = 255, nullable = false)
    private String handler;

    /**
     * JSON-схема параметров условия. Ни одна миграция схему не заполняет (в {@code guard_registry}
     * поле {@code null}), поэтому {@code PARAMS_INVALID} при валидации карты сейчас недостижим —
     * см. {@code 05_guards_actions_registry.md} §7 и README, раздел «Валидация карты переходов».
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params_schema")
    private Map<String, Object> paramsSchema;

    /**
     * {@link RegistryScope#GLOBAL} — встроенное условие, {@link RegistryScope#CUSTOM} —
     * пользовательское. См. {@code 05_guards_actions_registry.md} §3.2.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", length = 50, nullable = false)
    private RegistryScope scope;

    /**
     * Категории условия для группировки в UI ({@code AUTHORIZATION}, {@code VALIDATION},
     * {@code LIFECYCLE} и др. — {@code 05_guards_actions_registry.md} §3.3); на исполнение
     * перехода не влияют.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "categories", columnDefinition = "text[]", nullable = false)
    @Builder.Default
    private List<String> categories = List.of();

    /**
     * Типы сущностей ({@link ru.coordination.approval.domain.common.EntityType}), к которым
     * условие применимо. Проверяется валидатором карты: несовпадение даёт
     * {@code APPLICABILITY_MISMATCH} ({@code 05_guards_actions_registry.md} §7).
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "applicable_entities", columnDefinition = "text[]", nullable = false)
    @Builder.Default
    private List<String> applicableEntities = List.of();

    /**
     * Мягкое отключение записи. Неактивная запись трактуется как отсутствующая: разрешение кода
     * падает с {@code UnknownGuardException}, а повторная проверка выполняется на каждый переход,
     * а не только при валидации карты (см.
     * {@link ru.coordination.approval.engine.registry.GuardRegistry#resolve(String)}).
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Момент регистрации записи; аудит изменений реестра не ведётся. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Момент последнего изменения записи. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
