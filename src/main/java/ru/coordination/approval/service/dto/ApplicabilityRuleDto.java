package ru.coordination.approval.service.dto;

import java.util.List;
import java.util.Map;

/**
 * Правило применимости шаблона на входе {@link ru.coordination.approval.service.TemplateService}:
 * по нему {@code ApplicabilityRule} пересоздаётся в новой версии шаблона при обновлении и форке.
 *
 * <p>Дублирует по имени и составу полей {@code api.dto.template.ApplicabilityRuleDto}: различие в
 * том, что здесь нет {@code id} (строка создаётся заново, ADR-023) и типы не приведены к строкам.
 *
 * <p>{@code ruleIdx} задаёт приоритет правила при подборе шаблона — меньшее значение проверяется
 * раньше ({@code 03_domain_model.md} §6.6); {@code attributeConditions} — карта
 * «имя атрибута → условие с оператором» ({@code eq}/{@code gte}/{@code lte}/{@code in}),
 * разбираемая {@code MatchService}.
 */
public record ApplicabilityRuleDto(
    Integer ruleIdx,
    List<String> entityTypes,
    List<String> entitySubtypes,
    Map<String, Object> attributeConditions
) {}
