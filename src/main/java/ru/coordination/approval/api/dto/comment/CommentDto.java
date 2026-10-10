package ru.coordination.approval.api.dto.comment;

import java.time.Instant;
import java.util.UUID;

/**
 * Комментарий процесса ({@code 07_api_contract.md} §18.2) — в отличие от замечания не имеет
 * статусного жизненного цикла и не требует действий.
 *
 * @param remarkId всегда {@code null}: в {@code Comment} нет связи с замечанием, хотя контракт
 *                 предполагает комментарии к замечанию; поле существует только в DTO
 * @param stageId  {@code null}, если комментарий оставлен к процессу целиком, а не к этапу
 */
public record CommentDto(
        UUID id,
        UUID processId,
        UUID stageId,
        UUID remarkId,
        String text,
        UUID authorId,
        Instant createdAt
) {
}
