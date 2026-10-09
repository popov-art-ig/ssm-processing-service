package ru.coordination.approval.engine;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.coordination.approval.domain.registry.StatusRegistry;
import ru.coordination.approval.domain.registry.StatusRegistryId;

/**
 * Используется {@code TransitionEngine} для валидации {@code toState} перехода (PHASE-02,
 * раздел 6). {@code entityType} у {@link StatusRegistry} — {@code String}, не enum (так
 * задано схемой БД, см. тикет «Что уже есть»). Ключ составной: {@link StatusRegistryId}
 * ({@code code} + {@code entityType}) — один код статуса повторяется для разных агрегатов.
 */
public interface StatusRegistryRepository extends JpaRepository<StatusRegistry, StatusRegistryId> {

    boolean existsByCodeAndEntityType(String code, String entityType);
}
