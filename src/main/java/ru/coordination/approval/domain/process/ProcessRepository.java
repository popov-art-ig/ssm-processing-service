package ru.coordination.approval.domain.process;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Доступ к {@link ProcessInstance} — репозиторий агрегата, а не внутренний инструмент одного
 * сервиса (PHASE-03). {@link StageInstance}/{@link StageIteration}/{@link Participant} не
 * имеют отдельных репозиториев — сохраняются каскадом через {@code ProcessInstance.stages}
 * (см. {@code doc/tasks/PHASE-03-tasks.md} T3).
 */
public interface ProcessRepository extends JpaRepository<ProcessInstance, UUID>,
                                          JpaSpecificationExecutor<ProcessInstance> {

    List<ProcessInstance> findByTemplateRef(UUID templateRef);

    @Query("SELECT p FROM ProcessInstance p " +
           "LEFT JOIN FETCH p.stages s " +
           "LEFT JOIN FETCH s.iterations i " +
           "WHERE p.entityType = :entityType AND p.entityId = :entityId")
    Optional<ProcessInstance> findByEntityTypeAndEntityIdWithStagesAndIterations(
            @Param("entityType") String entityType,
            @Param("entityId") UUID entityId
    );

    Page<ProcessInstance> findByInitiatorIdOrResponsibleUserId(
            UUID initiatorId,
            UUID responsibleUserId,
            Pageable pageable
    );

    Page<ProcessInstance> findByInitiatorIdOrResponsibleUserIdAndStatus(
            UUID initiatorId,
            UUID responsibleUserId,
            String status,
            Pageable pageable
    );

    Optional<ProcessInstance> findByEntityTypeAndEntityId(String entityType, UUID entityId);
}
