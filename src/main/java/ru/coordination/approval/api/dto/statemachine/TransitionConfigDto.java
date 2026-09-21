package ru.coordination.approval.api.dto.statemachine;

import java.util.List;
import java.util.UUID;

public record TransitionConfigDto(
        UUID id,
        String transitionName,
        String fromState,
        String toState,
        String triggerType,
        List<String> guards,
        List<String> actions,
        List<String> emittedEvents
) {
}
