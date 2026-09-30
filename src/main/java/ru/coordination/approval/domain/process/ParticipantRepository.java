package ru.coordination.approval.domain.process;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Доступ к {@link Participant} напрямую — см. {@link StageRepository} за тем же обоснованием
 * (PHASE-04).
 */
public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    List<Participant> findByStageIterationIdIn(List<UUID> iterationIds);

    Page<Participant> findByUserIdAndStatus(UUID userId, String status, Pageable pageable);
}
