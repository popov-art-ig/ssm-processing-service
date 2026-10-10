package ru.coordination.approval.api.dto.process;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Этап процесса в детальном представлении ({@code 07_api_contract.md} §13.1) — этап вместе со всеми
 * своими итерациями.
 *
 * @param orderIdx     номер этапа в маршруте, начиная с 1 (не индекс массива): именно этим числом
 *                     оперирует переход {@code ResumeProcess} при выборе целевого этапа
 * @param duration     нормативная длительность этапа в <b>днях</b> — так её трактует action
 *                     {@code CalcStageDueAt} ({@code dueDate = startedAt + duration})
 * @param decisionMode режим агрегации решений ({@code ALL}/{@code ANY}/…); {@code null} у
 *                     {@code UNIFIED}, где решения собираются иначе
 */
public record StageDto(
        UUID id,
        Integer orderIdx,
        String name,
        String stageType,
        String status,
        Instant dueDate,
        Integer duration,
        String decisionMode,
        List<IterationDto> iterations
) {
}
