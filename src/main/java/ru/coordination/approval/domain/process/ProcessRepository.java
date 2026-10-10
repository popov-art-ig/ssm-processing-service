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

    /** Все процессы, стартовавшие по этой версии шаблона; используется guard'ом {@code NoActiveProcessesGuard}. */
    List<ProcessInstance> findByTemplateRef(UUID templateRef);

    /**
     * Процесс вместе с этапами и итерациями в одном запросе ({@code JOIN FETCH}) — снимок для
     * представления процесса в API без ленивых обращений вне транзакции (open-in-view выключен).
     */
    @Query("SELECT p FROM ProcessInstance p " +
           "LEFT JOIN FETCH p.stages s " +
           "LEFT JOIN FETCH s.iterations i " +
           "WHERE p.entityType = :entityType AND p.entityId = :entityId")
    Optional<ProcessInstance> findByEntityTypeAndEntityIdWithStagesAndIterations(
            @Param("entityType") String entityType,
            @Param("entityId") UUID entityId
    );

    /**
     * «Мои процессы» без фильтра по статусу: процессы, где пользователь инициатор <i>или</i>
     * ответственный, постранично. Два параметра передаются одним и тем же значением userId.
     */
    Page<ProcessInstance> findByInitiatorIdOrResponsibleUserId(
            UUID initiatorId,
            UUID responsibleUserId,
            Pageable pageable
    );

    /** То же с фильтром по статусу процесса; при {@code status == null} используется {@link #findByInitiatorIdOrResponsibleUserId}. */
    Page<ProcessInstance> findByInitiatorIdOrResponsibleUserIdAndStatus(
            UUID initiatorId,
            UUID responsibleUserId,
            String status,
            Pageable pageable
    );

    /**
     * Процесс по внешней сущности — поиск «есть ли уже процесс на этот документ». Ожидается не
     * более одного процесса на пару: при нарушении этого ожидания метод бросает
     * {@code IncorrectResultSizeDataAccessException} (см. замечание в
     * {@code ApprovalViewController}).
     */
    Optional<ProcessInstance> findByEntityTypeAndEntityId(String entityType, UUID entityId);
}
