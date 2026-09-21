package ru.coordination.approval.api.mapper;

import org.springframework.stereotype.Component;
import ru.coordination.approval.api.dto.decision.DecisionDto;
import ru.coordination.approval.domain.process.Decision;
import ru.coordination.approval.domain.process.DecisionRepository;
import ru.coordination.approval.domain.process.Participant;

@Component
public class DecisionMapper {

    private final DecisionRepository decisionRepository;

    public DecisionMapper(DecisionRepository decisionRepository) {
        this.decisionRepository = decisionRepository;
    }

    public DecisionDto toDto(Decision decision) {
        return new DecisionDto(
                decision.getId(),
                decision.getParticipant().getId(),
                decision.getDecision(),
                decision.getComment(),
                decision.getDecidedAt(),
                decision.getDecidedBy()
        );
    }

    public Boolean hasDecision(Participant participant) {
        return decisionRepository.findById(participant.getId()).isPresent();
    }
}
