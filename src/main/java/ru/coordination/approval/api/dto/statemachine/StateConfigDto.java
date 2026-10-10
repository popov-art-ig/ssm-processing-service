package ru.coordination.approval.api.dto.statemachine;

import java.util.UUID;

/**
 * Состояние карты переходов ({@code 07_api_contract.md} §23.2).
 *
 * @param stateName код состояния из {@code state_config.code}; строки состояний совпадают со
 *                  значениями {@code status_registry} для того же {@code entityType}, поэтому
 *                  статус из ответа API можно сопоставлять с состоянием карты напрямую
 */
public record StateConfigDto(
        UUID id,
        String stateName
) {
}
