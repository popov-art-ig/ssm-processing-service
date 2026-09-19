package ru.coordination.approval.engine.registry;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.coordination.approval.domain.registry.GuardRegistryEntry;

public interface GuardRegistryRepository extends JpaRepository<GuardRegistryEntry, String> {
}
