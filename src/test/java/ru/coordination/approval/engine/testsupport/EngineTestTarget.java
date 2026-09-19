package ru.coordination.approval.engine.testsupport;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Минимальная тестовая JPA-сущность с {@code status} и оптимистичной блокировкой
 * (PHASE-02, T7/T8) — демонстрирует, что {@code TransitionEngine} работает с любой
 * обобщённой сущностью через {@code Consumer<String>}, не только с {@code domain.process.*}.
 * Таблица создаётся отдельной тестовой Flyway-миграцией
 * ({@code db/test-migration/V900__engine_test_target.sql}), не входит в production-схему.
 */
@Entity
@Table(name = "engine_test_target")
@Getter
@Setter
@NoArgsConstructor
public class EngineTestTarget {

    @Id
    private UUID id;

    @Column(name = "status", nullable = false)
    private String status;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    public EngineTestTarget(UUID id, String status) {
        this.id = id;
        this.status = status;
    }
}
