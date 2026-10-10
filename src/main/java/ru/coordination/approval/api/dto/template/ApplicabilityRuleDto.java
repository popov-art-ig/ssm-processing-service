package ru.coordination.approval.api.dto.template;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Правило применимости шаблона ({@code 07_api_contract.md} §10.3) — условие, по которому при
 * создании процесса подбирается шаблон (группа 2 контракта, подбор шаблона).
 *
 * @param entityTypes         типы сущностей, к которым применимо правило
 * @param entitySubtypes      подтипы сущностей; пустой список — уточнение не задано
 * @param attributeConditions условия на атрибуты снимка сущности, вложенная структура «поле →
 *                            ожидаемое значение»; сравнение выполняет подбор шаблона, DTO только
 *                            переносит структуру
 * @param ruleIdx             порядок применения правила внутри шаблона
 */
public record ApplicabilityRuleDto(
        UUID id,
        Integer ruleIdx,
        List<String> entityTypes,
        List<String> entitySubtypes,
        Map<String, Object> attributeConditions
) {
}
