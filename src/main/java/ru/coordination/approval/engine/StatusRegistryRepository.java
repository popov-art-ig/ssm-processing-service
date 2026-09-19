package ru.coordination.approval.engine;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.coordination.approval.domain.registry.StatusRegistry;

/**
 * Используется {@code TransitionEngine} для валидации {@code toState} перехода (PHASE-02,
 * раздел 6). {@code entityType} у {@link StatusRegistry} — {@code String}, не enum (так
 * задано схемой БД, см. тикет «Что уже есть»).
 */
public interface StatusRegistryRepository extends JpaRepository<StatusRegistry, String> {

    boolean existsByCodeAndEntityType(String code, String entityType);
}
