package ru.coordination.approval.dto.view;

import ru.coordination.approval.dto.process.StageDto;
import java.util.List;

public record StageWithDecisionsDto(
        StageDto stage,
        List<ParticipantWithDecisionDto> participants
) {
}
