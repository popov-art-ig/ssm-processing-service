package ru.coordination.approval.api.dto.view;

import java.time.Instant;
import java.util.UUID;

/**
 * Задача пользователя для списка «Мои задачи» ({@code 07_api_contract.md} §25, ADR-020).
 *
 * <p>Отдельной сущности «задача» в модели нет: это проекция участника в статусе {@code Assigned}
 * вместе с его этапом и процессом, собранная одним запросом.
 *
 * @param taskId   совпадает с {@code participantId}: идентификатором задачи служит участник
 * @param dueDate  персональный срок участника, а не этапа: именно он определяет «горящую» задачу
 * @param status   статус участника ({@code Assigned}), а не процесса
 */
public record TaskDto(
        UUID taskId,
        UUID processId,
        UUID stageId,
        UUID participantId,
        String processEntityType,
        UUID processEntityId,
        String stageName,
        Instant dueDate,
        String status
) {
}
