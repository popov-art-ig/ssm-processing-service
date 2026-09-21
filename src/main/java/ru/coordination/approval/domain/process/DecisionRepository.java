package ru.coordination.approval.domain.process;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {
    Optional<Decision> findByParticipantId(UUID participantId);

    List<Decision> findByParticipantIdIn(List<UUID> participantIds);

    @Query("SELECT d FROM Decision d JOIN d.participant p JOIN p.stageIteration i JOIN i.stage s WHERE s.process.id = :processId")
    List<Decision> findByProcessId(@Param("processId") UUID processId);
}
