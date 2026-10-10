package ru.coordination.approval.engine;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.coordination.approval.domain.audit.AuditEvent;

/**
 * Запись {@link AuditEvent} на каждый выполненный переход (шаг 3c алгоритма
 * {@code 04_state_machines.md} §3.3). Используется {@code TransitionEngine}; аудит обязателен
 * для каждого перехода, поэтому запись не опциональна и не может быть отложена вызывающим
 * кодом — она идёт в той же транзакции, что и смена статуса (ADR-028).
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
}
