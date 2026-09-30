package ru.coordination.approval.api.dto.view;

import ru.coordination.approval.api.dto.decision.DecisionDto;
import ru.coordination.approval.api.dto.process.ParticipantDto;

public record ParticipantWithDecisionDto(
        ParticipantDto participant,
        DecisionDto decision
) {
}
