package ru.coordination.approval.domain.process;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Доступ к {@link Participant} напрямую — см. {@link StageRepository} за тем же обоснованием
 * (PHASE-04).
 */
public interface ParticipantRepository extends JpaRepository<Participant, UUID> {
}
