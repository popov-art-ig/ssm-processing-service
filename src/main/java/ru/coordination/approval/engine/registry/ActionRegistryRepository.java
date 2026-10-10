package ru.coordination.approval.engine.registry;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.coordination.approval.domain.registry.ActionRegistryEntry;

/**
 * Доступ к {@code action_registry} для {@link ActionRegistry} (PHASE-02, раздел 4). Ключ записи —
 * её {@code code} (строка, как в {@code transition_config.actions[]}), поэтому {@code findById}
 * ищет action по коду, а не по суррогатному идентификатору.
 */
public interface ActionRegistryRepository extends JpaRepository<ActionRegistryEntry, String> {
}
