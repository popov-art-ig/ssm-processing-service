package ru.coordination.approval.service.dto;

import java.util.List;
import java.util.UUID;
import ru.coordination.approval.domain.common.ProcessType;

/**
 * Запрос {@link ru.coordination.approval.service.TemplateService#create} на создание шаблона
 * маршрута: результат всегда новая строка {@code Template} в статусе {@code DRAFT} с
 * {@code version = 1} и {@code parentTemplateId = null} ({@code 07_api_contract.md} §10.3).
 *
 * <p>Дублирует {@code api.dto.template.CreateTemplateRequest}, но без bean-валидации и с доменным
 * {@link ProcessType} вместо строки. {@code createdBy} — автор шаблона; он же подставляется в
 * {@code createdBy} новых версий при обновлении и форке.
 */
public record TemplateCreateRequest(
    String name,
    ProcessType processType,
    Boolean processIterationEnabled,
    List<UUID> responsibleRoles,
    Boolean requiresResponsibleApproval,
    List<StageTemplateDto> stages,
    List<ApplicabilityRuleDto> applicabilityRules,
    UUID createdBy
) {}
