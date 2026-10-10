package ru.coordination.approval.api.dto.process;

import java.time.Instant;
import java.util.UUID;

/**
 * Участник итерации этапа ({@code 07_api_contract.md} §14.1) — основной согласующий или
 * подписант.
 *
 * @param hasDecision признак «решение уже записано», вычисляемый отдельным обращением к
 *                    {@code decision}, а не по {@code status}: движок пишет статус до выполнения
 *                    actions, поэтому сразу после голосования статус ещё может быть прежним
 * @param dueDate     персональный срок участника, который и определяет «горящую» задачу
 *                    (в отличие от {@code StageDto.dueDate})
 */
public record ParticipantDto(
        UUID id,
        Integer orderIdx,
        UUID userId,
        UUID organizationId,
        String status,
        Instant dueDate,
        Boolean hasDecision
) {
}
