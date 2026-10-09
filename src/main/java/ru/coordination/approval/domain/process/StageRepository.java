package ru.coordination.approval.domain.process;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Доступ к {@link StageInstance} напрямую (не только через каскад
 * {@code ProcessInstance.stages}) — нужен там, где известен id конкретного этапа
 * (PHASE-04, например {@code ProcessServiceIntegrationTest}), без ленивой навигации по
 * detached {@link ProcessInstance}.
 */
public interface StageRepository extends JpaRepository<StageInstance, UUID> {

    /**
     * Идентификаторы более ранних этапов процесса, отсортированные по {@code orderIdx} по убыванию.
     *
     * <p>Для {@code PreviousStageCompletedGuard}: guard вызывается как action перехода закрытия
     * этапа, то есть ДО того, как {@code TransitionEngine} запишет этапу новый статус. Поэтому
     * статус предыдущего этапа нужно читать из БД, а не из {@code process.getStages()} —
     * там он ещё старый (все сценарии активации следующего этапа падали).
     */
    @Query("select s.id from StageInstance s where s.process.id = :processId and s.orderIdx < :orderIdx "
            + "order by s.orderIdx desc")
    List<UUID> findPreviousStageIds(@Param("processId") UUID processId, @Param("orderIdx") int orderIdx);

    /** Этапы процесса по порядку — свежее чтение (без ленивой навигации по {@code ProcessInstance}). */
    @Query("select s from StageInstance s where s.process.id = :processId order by s.orderIdx")
    List<StageInstance> findByProcessIdOrderByOrderIdx(@Param("processId") UUID processId);

    /** Число итераций этапа — проверка создания новой итерации при возобновлении. */
    @Query("select count(i) from StageIteration i where i.stage.id = :stageId")
    long countIterations(@Param("stageId") UUID stageId);

    /**
     * Свежие статусы всех этапов процесса.
     *
     * <p>Нужен guard'ам, которые вызываются как actions переходов закрытия этапа:
     * {@code TransitionEngine} записывает новый статус этапа только после выполнения actions,
     * поэтому {@code process.getStages()} в этот момент содержит устаревшие значения.
     */
    @Query("select s.status from StageInstance s where s.process.id = :processId")
    List<String> findStatusesByProcessId(@Param("processId") UUID processId);
}
