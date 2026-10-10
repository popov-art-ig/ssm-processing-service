package ru.coordination.approval.api.dto.template;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Шаблон маршрута в ответах {@code TemplateController} ({@code 07_api_contract.md} §10.2) — с
 * этапами и правилами применимости, целиком.
 *
 * @param version          номер версии; правка опубликованного шаблона создаёт новую строку с
 *                         {@code version + 1} (ADR-023), поэтому {@code id} в ответе может
 *                         отличаться от {@code id} в запросе — это не ошибка
 * @param parentTemplateId предшественник версии: заполнен у шаблонов, созданных обновлением или
 *                         форком
 * @param status           код жизненного цикла {@code DRAFT}/{@code PUBLISHED}/{@code DEPRECATED}/
 *                         {@code ARCHIVED} из {@code status_registry} для {@code TEMPLATE}
 */
public record TemplateDto(
        UUID id,
        String name,
        String processType,
        String status,
        Integer version,
        UUID parentTemplateId,
        Boolean processIterationEnabled,
        Instant publishedAt,
        List<StageTemplateDto> stages,
        List<ApplicabilityRuleDto> applicabilityRules
) {
}
