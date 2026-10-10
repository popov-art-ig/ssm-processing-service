package ru.coordination.approval.api.dto.statemachine;

import java.util.List;
import java.util.UUID;

/**
 * Версия карты переходов вместе с состояниями и переходами ({@code 07_api_contract.md} §23.2) —
 * читаемая проекция конфигурации, по которой движок строит модель.
 *
 * @param processType {@code null} для карт, не привязанных к типу процесса (например
 *                    {@code entity_type = TEMPLATE}, V25): поле nullable по данным, а не по ошибке
 *                    маппинга
 * @param version     номер версии конфигурации; {@code ProcessInstance.configVersion} фиксирует,
 *                    по какой именно версии работает процесс (Snapshot-on-Start, ADR-003), поэтому
 *                    «последняя» версия в списке не обязательно действующая
 */
public record StateMachineConfigDto(
        UUID id,
        String entityType,
        String processType,
        Integer version,
        List<StateConfigDto> states,
        List<TransitionConfigDto> transitions
) {
}
