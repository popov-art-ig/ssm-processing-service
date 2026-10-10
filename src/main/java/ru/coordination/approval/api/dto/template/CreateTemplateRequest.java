package ru.coordination.approval.api.dto.template;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Тело запроса на создание шаблона ({@code 07_api_contract.md} §10.3) — шаблон создаётся в статусе
 * {@code DRAFT} и версии 1.
 *
 * @param processType               {@code STANDARD} или {@code UNIFIED}; от него зависит, какие
 *                                  поля этапов допустимы (проверяет action
 *                                  {@code ValidateTemplateStructure})
 * @param responsibleRoles          роли ответственного за маршрут; используются для {@code UNIFIED},
 *                                  где маршрут согласует ответственный
 * @param requiresResponsibleApproval требует ли маршрут отдельного согласования ответственным
 * @param createdBy                 автор шаблона: берётся из тела, а не из аутентификации — её в
 *                                  этой фазе нет
 */
public record CreateTemplateRequest(
        @NotBlank String name,
        @NotBlank String processType,
        Boolean processIterationEnabled,
        List<UUID> responsibleRoles,
        Boolean requiresResponsibleApproval,
        @NotNull List<StageTemplateDto> stages,
        List<ApplicabilityRuleDto> applicabilityRules,
        @NotNull UUID createdBy
) {
}
