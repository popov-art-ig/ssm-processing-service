package ru.coordination.approval.api.dto.statemachine;

import java.util.List;
import java.util.UUID;

public record StateMachineConfigDto(
        UUID id,
        String entityType,
        String processType,
        Integer version,
        List<StateConfigDto> states,
        List<TransitionConfigDto> transitions
) {
}
