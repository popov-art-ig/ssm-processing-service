package ru.coordination.approval.dto.generation;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Снимок сущности внешней системы, по которому подбирается шаблон: DTO-граница между вызывающим
 * кодом и сервисом подбора ({@code 09_adapters.md} §6.1, §6.4).
 *
 * <p><b>Кто создаёт и кто потребляет.</b> Создаётся вызывающим кодом:
 * {@code ProcessService.createProcess} собирает его из тела запроса
 * ({@code CreateProcessRequest}), а {@link ru.coordination.approval.adapter.EntityAdapter}
 * (фактически {@code EntityAdapterStub}) — из ответа внешней системы. Потребляется
 * {@code MatchService.findMatchingTemplates} (сверка типа, подтипа и атрибутов с правилами
 * применимости) и {@code RouteGenerationService} (передаётся в
 * {@link ru.coordination.approval.adapter.RoleResolverAdapter} как контекст резолвинга).
 *
 * <p><b>Состав полей не совпадает с моделью {@code 09_adapters.md} §6.4</b> (там
 * {@code EntityRef + attributes + metadata}): вместо {@code EntityRef} — плоские
 * {@code entityType}/{@code entitySubtype}/{@code entityId}, вместо {@code metadata} — поле
 * {@code fetchedAt}.
 *
 * @param entityType     тип сущности внешней системы; сравнивается с
 *                       {@code ApplicabilityRule.entityTypes}, {@code null} не предполагается
 * @param entitySubtype  подтип сущности; {@code null} <b>допустим</b> и означает «подтип не задан»:
 *                       если правило применимости требует конкретный подтип, оно не сработает
 *                       (см. {@code MatchService.ruleMatches})
 * @param entityId       идентификатор сущности во внешней системе
 * @param attributes     атрибуты для подбора шаблона; {@code null} <b>недопустим</b>, если хотя бы
 *                       одно правило применимости задаёт условия на атрибуты: в этом случае карта
 *                       читается без проверки на {@code null} ({@code MatchService.attributeMatches}),
 *                       поэтому вызывающий обязан передать хотя бы пустую карту
 *                       ({@code ProcessService} так и делает)
 * @param fetchedAt      момент получения снимка; заполняется вызывающим кодом и в текущей
 *                       реализации нигде не читается
 */
public record EntitySnapshot(
        String entityType,
        String entitySubtype, // nullable
        UUID entityId,
        Map<String, Object> attributes,
        Instant fetchedAt
) {
}
