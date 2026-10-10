package ru.coordination.approval.api.dto.remark;

import java.time.Instant;
import java.util.UUID;

/**
 * Замечание процесса ({@code 07_api_contract.md} §17.2). Два поля здесь всегда пусты, потому что
 * одноимённых данных нет в сущности {@code Remark}: DTO описывает контракт, а не текущую модель.
 *
 * @param severity       всегда {@code null}: степени важности
 *                       {@code CRITICAL}/{@code MAJOR}/{@code MINOR} в {@code remark} нет
 * @param resolutionText всегда {@code null}: текст исправления не хранится, есть только отметки
 *                       {@code resolvedAt}/{@code resolvedBy}
 * @param status         код из {@code status_registry} для {@code REMARK} ({@code Proposed},
 *                       {@code Active}, {@code InProgress}, {@code Processed}, {@code Rejected},
 *                       {@code NotRequired})
 */
public record RemarkDto(
        UUID id,
        UUID processId,
        UUID stageId,
        String text,
        String severity,
        String status,
        UUID authorId,
        Instant createdAt,
        String resolutionText,
        Instant resolvedAt
) {
}
