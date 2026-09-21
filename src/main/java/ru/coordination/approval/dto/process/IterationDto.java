package ru.coordination.approval.dto.process;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IterationDto(
        UUID id,
        Integer iterationIdx,
        String status,
        Instant dueDate,
        List<ParticipantDto> participants
) {
}
