package ru.coordination.approval.dto.view;

import ru.coordination.approval.dto.decision.DecisionDto;
import ru.coordination.approval.dto.process.ParticipantDto;

public record ParticipantWithDecisionDto(
        ParticipantDto participant,
        DecisionDto decision
) {
}
