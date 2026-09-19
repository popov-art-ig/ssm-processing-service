package ru.coordination.approval.engine.testsupport;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EngineTestTargetRepository extends JpaRepository<EngineTestTarget, UUID> {
}
