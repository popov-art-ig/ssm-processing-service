package ru.coordination.approval.engine.model;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;

/**
 * Доступ к {@link StateMachineConfig}, используемый {@code ModelFactory}
 * (PHASE-02, `engine/model`).
 */
public interface StateMachineConfigRepository extends JpaRepository<StateMachineConfig, UUID> {
}
