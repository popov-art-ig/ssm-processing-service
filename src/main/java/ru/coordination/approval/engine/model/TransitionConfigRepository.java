package ru.coordination.approval.engine.model;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.coordination.approval.domain.statemachine.TransitionConfig;

/**
 * Доступ к {@link TransitionConfig}, используемый {@code ModelFactory} (PHASE-02,
 * `engine/model`). См. {@link StateConfigRepository} за объяснением раздельных запросов.
 */
public interface TransitionConfigRepository extends JpaRepository<TransitionConfig, UUID> {

    @Query("select t from TransitionConfig t where t.config.id = :configId")
    List<TransitionConfig> findByConfigId(@Param("configId") UUID configId);
}
