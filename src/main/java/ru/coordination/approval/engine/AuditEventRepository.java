package ru.coordination.approval.engine;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.coordination.approval.domain.audit.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
}
