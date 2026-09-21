package ru.coordination.approval.domain.process;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {
    Optional<Decision> findByParticipantId(UUID participantId);
}
