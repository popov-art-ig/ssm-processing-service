package ru.coordination.approval.service.dto;

import java.util.List;
import ru.coordination.approval.domain.common.DecisionMode;
import ru.coordination.approval.domain.common.ExecutionOrder;
import ru.coordination.approval.domain.common.StageType;

/**
 * Описание этапа на входе {@link ru.coordination.approval.service.TemplateService} — из него
 * создаётся строка {@code StageTemplate} со слотами ({@code 03_domain_model.md} §6.4).
 *
 * <p>Дублирует {@code api.dto.template.StageTemplateDto}, но без {@code id} и с доменными
 * перечислениями {@link StageType}, {@link DecisionMode}, {@link ExecutionOrder} вместо строк,
 * которыми они представлены в API-контракте ({@code 07_api_contract.md} §10.2).
 *
 * <p>{@code duration} — срок в днях, обязателен для положительного значения; {@code
 * allowedReturnStages} — {@code orderIdx} этапов, допустимых как цель возврата, только прошлых и
 * текущего ({@code 03_domain_model.md} §6.4).
 */
public record StageTemplateDto(
    Integer orderIdx,
    String name,
    String description,
    StageType stageType,
    Integer duration,
    DecisionMode decisionMode,
    ExecutionOrder executionOrder,
    Boolean isMandatory,
    Boolean isOrderMandatory,
    List<Integer> allowedReturnStages,
    List<SlotTemplateDto> actorSlots
) {}
