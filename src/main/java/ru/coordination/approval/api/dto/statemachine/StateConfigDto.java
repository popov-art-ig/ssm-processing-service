package ru.coordination.approval.api.dto.statemachine;

import java.util.UUID;

public record StateConfigDto(
        UUID id,
        String stateName
) {
}
