package ru.coordination.approval.api.dto.template;

import java.util.List;
import java.util.UUID;

/**
 * Этап шаблона — одновременно элемент ответа на чтение ({@code 07_api_contract.md} §10.2) и часть
 * тела запроса на создание/обновление (§10.3–10.4).
 *
 * <p>Двустороннее использование объясняет, почему {@code id} заполнен только при чтении: при
 * создании шаблона идентификаторов этапов ещё не существует, и сервис игнорирует поле.
 *
 * @param duration            нормативная длительность этапа в <b>днях</b>: при генерации маршрута
 *                            переносится в {@code StageInstance.duration}, откуда
 *                            {@code CalcStageDueAt} считает срок
 * @param executionOrder      порядок исполнения этапа ({@code ALL}/{@code ANY}/…); обязателен для
 *                            {@code STANDARD} и запрещён для {@code UNIFIED} — это проверяет action
 *                            {@code ValidateTemplateStructure} при публикации
 * @param allowedReturnStages номера этапов, на которые разрешён возврат; сверяются с ними при
 *                            возобновлении процесса (guard {@code IsTargetStageAllowedGuard})
 * @param actorSlots          слоты участников; вложенные доп. согласующие в этом DTO не
 *                            разворачиваются — см. Javadoc {@code TemplateMapper#toSlotTemplateDto}
 */
public record StageTemplateDto(
        UUID id,
        Integer orderIdx,
        String name,
        String description,
        String stageType,
        Integer duration,
        String decisionMode,
        String executionOrder,
        Boolean isMandatory,
        Boolean isOrderMandatory,
        List<Integer> allowedReturnStages,
        List<SlotTemplateDto> actorSlots
) {
}
