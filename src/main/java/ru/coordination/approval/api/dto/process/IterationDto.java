package ru.coordination.approval.api.dto.process;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Итерация этапа ({@code 07_api_contract.md} §13.3) — один «заход» согласования: новая итерация
 * появляется при повторной активации этапа после возврата на доработку, что позволяет сохранить
 * решения предыдущего круга (ADR-009).
 *
 * @param iterationIdx номер итерации, начиная с 1; этап со статусом {@code Active} имеет ровно одну
 *                     активную итерацию
 * @param dueDate      всегда {@code null}: у {@code StageIteration} нет поля срока, сроки хранятся
 *                     на этапе и на участнике (см. Javadoc {@code ProcessMapper#toIterationDto})
 */
public record IterationDto(
        UUID id,
        Integer iterationIdx,
        String status,
        Instant dueDate,
        List<ParticipantDto> participants
) {
}
