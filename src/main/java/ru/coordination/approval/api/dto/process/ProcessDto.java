package ru.coordination.approval.api.dto.process;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Детальное представление процесса для UI: агрегат {@code ProcessInstance} вместе с этапами,
 * итерациями и участниками (ADR-020; {@code 07_api_contract.md} §12.3).
 *
 * <p>Корневой элемент ответов {@code ProcessController}; на экране согласования вложен в
 * {@link ru.coordination.approval.api.dto.view.ApprovalViewDto}. Решения в это представление не
 * входят — их отдают {@code DecisionDto} и агрегированные view-DTO.
 *
 * @param processType код {@code ProcessType} строкой ({@code STANDARD}/{@code UNIFIED}): тип
 *                    фиксируется при создании процесса по шаблону и дальше не меняется
 * @param status      код из {@code status_registry} для {@code entityType = PROCESS}
 *                    ({@code Draft}/{@code InProgress}/{@code OnRework}/{@code Approved}/…)
 * @param stages      все этапы маршрута, в том числе ещё не активированные; порядок задаётся
 *                    {@code orderIdx}, а не временем активации
 */
public record ProcessDto(
        UUID id,
        String entityType,
        String entitySubtype,
        UUID entityId,
        String processType,
        String status,
        UUID initiatorId,
        UUID responsibleUserId,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        List<StageDto> stages
) {
}
