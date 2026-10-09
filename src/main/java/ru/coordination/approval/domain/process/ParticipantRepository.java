package ru.coordination.approval.domain.process;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Доступ к {@link Participant} напрямую — см. {@link StageRepository} за тем же обоснованием
 * (PHASE-04).
 */
public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    List<Participant> findByStageIterationIdIn(List<UUID> iterationIds);

    Page<Participant> findByUserIdAndStatus(UUID userId, String status, Pageable pageable);

    /** Число участников итерации — знаменатель для проверки «все приняли решение». */
    @Query("select count(p) from Participant p where p.stageIteration.id = :iterationId")
    long countByStageIterationId(@Param("iterationId") UUID iterationId);

    /**
     * Число участников итерации, кроме указанного.
     *
     * <p>Парная операция к {@code DecisionRepository.countByStageIterationIdExcluding}:
     * guard агрегации сравнивает «сколько ещё не проголосовало» с «сколько уже проголосовало»,
     * исключая сущность текущего перехода (её статус движок записывает до выполнения actions).
     */
    @Query("select count(p) from Participant p where p.stageIteration.id = :iterationId "
            + "and p.id <> :excludedParticipantId")
    long countByStageIterationIdExcluding(
            @Param("iterationId") UUID iterationId,
            @Param("excludedParticipantId") UUID excludedParticipantId);
}
