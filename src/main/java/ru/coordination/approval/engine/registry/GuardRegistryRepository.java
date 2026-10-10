package ru.coordination.approval.engine.registry;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.coordination.approval.domain.registry.GuardRegistryEntry;

/**
 * Доступ к {@code guard_registry} для {@link GuardRegistry} (PHASE-02, раздел 4). Ключ записи —
 * её {@code code} (строка, как в {@code transition_config.guards[]}), поэтому {@code findById}
 * ищет guard по коду, а не по суррогатному идентификатору.
 */
public interface GuardRegistryRepository extends JpaRepository<GuardRegistryEntry, String> {
}
