package ru.coordination.approval.api.dto.template;

import java.util.List;
import java.util.UUID;

/**
 * Тело запроса на обновление шаблона ({@code 07_api_contract.md} §10.4).
 *
 * <p>Отличие от {@link CreateTemplateRequest}: нет {@code processType} и {@code createdBy} — тип
 * процесса и автор не меняются. По {@code DRAFT} правка идёт на месте, по {@code PUBLISHED}
 * создаётся новая версия (ADR-023).
 *
 * @param stages               новый состав этапов; {@code null} трактуется сервисом как «не
 *                             передано», а не как «очистить»
 * @param applicabilityRules   новые правила подбора шаблона; та же семантика {@code null}
 */
public record UpdateTemplateRequest(
        String name,
        Boolean processIterationEnabled,
        List<UUID> responsibleRoles,
        Boolean requiresResponsibleApproval,
        List<StageTemplateDto> stages,
        List<ApplicabilityRuleDto> applicabilityRules
) {
}
