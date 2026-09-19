package ru.coordination.approval.engine.model;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;

/**
 * Доступ к {@link StateMachineConfig}, используемый {@code ModelFactory}
 * (PHASE-02, `engine/model`).
 */
public interface StateMachineConfigRepository extends JpaRepository<StateMachineConfig, UUID> {

    /**
     * Резолвит конкретную версию конфига по бизнес-ключу (PHASE-03,
     * {@code ru.coordination.approval.service.ProcessService}) — вызывающий код обычно знает
     * {@code (entityType, processType, version)} сущности (например,
     * {@code ProcessInstance.configVersion}), а не UUID конфига напрямую. Уникальность
     * гарантирована индексом {@code ux_state_machine_config_version}.
     */
    Optional<StateMachineConfig> findByEntityTypeAndProcessTypeAndVersion(
            EntityType entityType, ProcessType processType, Integer version);
}
