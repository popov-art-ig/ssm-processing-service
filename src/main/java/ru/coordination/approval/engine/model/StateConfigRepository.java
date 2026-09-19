package ru.coordination.approval.engine.model;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.coordination.approval.domain.statemachine.StateConfig;

/**
 * Доступ к {@link StateConfig}, используемый {@code ModelFactory} (PHASE-02, `engine/model`).
 *
 * <p>Отдельный запрос вместо {@code join fetch} обеих коллекций {@code StateMachineConfig}
 * (states + transitions) за один раз — Hibernate не может сделать {@code join fetch} двух
 * {@code List}-коллекций одновременно ({@code MultipleBagFetchException}).
 */
public interface StateConfigRepository extends JpaRepository<StateConfig, UUID> {

    @Query("select s from StateConfig s where s.config.id = :configId")
    List<StateConfig> findByConfigId(@Param("configId") UUID configId);
}
