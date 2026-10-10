package ru.coordination.approval.domain.registry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
 * Справочник результатов решений (RegistryAggregate).
 * Значения по умолчанию: APPROVE, APPROVE_WITH_COMMENTS, REJECT (ABSTAIN убран, см. 08_db_schema.md §5.2).
 *
 * <p>Источник истины для строковых полей {@code decision.result} и
 * {@code final_decision.result}: классы {@link ru.coordination.approval.domain.process.Decision} и
 * {@link ru.coordination.approval.domain.process.FinalDecision} хранят результат как код реестра,
 * а не как enum. Обратите внимание: на код этого реестра ссылается и поле
 * {@link ru.coordination.approval.domain.process.Participant#getDecision()} (архитектурная модель
 * {@code participant.decision}), но фактически решения пишутся отдельной строкой {@code decision} —
 * см. {@code 08_db_schema.md} §11.1.
 *
 * См. 03_domain_model.md §5.3, 08_db_schema.md §5.2.
 */
@Entity
@Table(name = "decision_result_registry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionResultRegistry {

    /** Код результата: {@code APPROVE}, {@code APPROVE_WITH_COMMENTS}, {@code REJECT}. */
    @Id
    @Column(name = "code", length = 100, nullable = false)
    private String code;

    /** Название для UI («Согласовать», «Согласовать с замечаниями», «Отклонить»). */
    @Column(name = "display_name", length = 255, nullable = false)
    private String displayName;

    /** Пояснение для администратора. */
    @Column(name = "description")
    private String description;

    /**
     * Признак положительного результата ({@code APPROVE}, {@code APPROVE_WITH_COMMENTS}).
     * Справочная информация: агрегация решений сравнивает {@code result} с конкретными кодами
     * ({@code "APPROVE"}, {@code "APPROVE_WITH_COMMENTS"}, {@code "REJECT"}) и это поле не читает.
     */
    @Column(name = "is_positive", nullable = false)
    @Builder.Default
    private boolean positive = false;

    /** Признак отрицательного результата ({@code REJECT}); так же не участвует в агрегации. */
    @Column(name = "is_negative", nullable = false)
    @Builder.Default
    private boolean negative = false;

    /** Признак терминальности результата (у всех трёх значений по умолчанию {@code true}). */
    @Column(name = "is_terminal", nullable = false)
    @Builder.Default
    private boolean terminal = true;

    /** Дополнительные атрибуты результата (JSON); миграции заполняют пустым объектом. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    /** Мягкое отключение результата; кодом не читается — см. {@code is_active} в StatusRegistry. */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Момент добавления записи в справочник. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
