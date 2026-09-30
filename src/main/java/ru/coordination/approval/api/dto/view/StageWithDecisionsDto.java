package ru.coordination.approval.api.dto.view;

import ru.coordination.approval.api.dto.process.StageDto;

import java.util.List;

public record StageWithDecisionsDto(
        StageDto stage,
        List<ParticipantWithDecisionDto> participants
) {
}
