package ru.coordination.approval.api.dto.view;

import java.time.Instant;
import java.util.UUID;

/**
 * Краткая карточка процесса для списка «Мои процессы» ({@code 07_api_contract.md} §25, ADR-020).
 *
 * <p>Отличие от {@link ru.coordination.approval.api.dto.process.ProcessDto}: нет этапов, итераций и
 * участников — только то, что помещается в строку списка. Именно поэтому это отдельный DTO, а не
 * урезанный маппинг детального представления.
 *
 * @param dueDate          всегда {@code null}: срок процесса в сервисе не вычисляется
 * @param currentStageName имя первого этапа в статусе {@code Active}; {@code null}, если активного
 *                         этапа нет (процесс завершён или отозван)
 */
public record ProcessSummaryDto(
        UUID id,
        String entityType,
        UUID entityId,
        String status,
        Instant createdAt,
        Instant dueDate,
        String currentStageName
) {
}
