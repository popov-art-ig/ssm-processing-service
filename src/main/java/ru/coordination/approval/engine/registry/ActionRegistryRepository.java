package ru.coordination.approval.engine.registry;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.coordination.approval.domain.registry.ActionRegistryEntry;

public interface ActionRegistryRepository extends JpaRepository<ActionRegistryEntry, String> {
}
