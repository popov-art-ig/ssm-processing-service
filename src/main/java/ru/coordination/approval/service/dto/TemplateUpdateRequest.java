package ru.coordination.approval.service.dto;

import java.util.List;
import java.util.UUID;

/**
 * Запрос {@link ru.coordination.approval.service.TemplateService#update} на изменение шаблона;
 * применяется двумя разными способами в зависимости от статуса: правка на месте для {@code DRAFT}
 * и создание новой версии для остальных статусов (ADR-023, {@code 07_api_contract.md} §10.4).
 *
 * <p>Дублирует {@code api.dto.template.UpdateTemplateRequest}. {@code name} может быть
 * {@code null}: при создании новой версии тогда сохраняется имя предыдущей.
 */
public record TemplateUpdateRequest(
    String name,
    Boolean processIterationEnabled,
    List<UUID> responsibleRoles,
    Boolean requiresResponsibleApproval,
    List<StageTemplateDto> stages,
    List<ApplicabilityRuleDto> applicabilityRules
) {}
