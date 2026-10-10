package ru.coordination.approval.dto.generation;

import java.util.UUID;

/**
 * Запрос на генерацию маршрута процесса: DTO-граница между вызывающим кодом и
 * {@code RouteGenerationService.generateRoute} (PHASE-15; {@code 10_architecture.md} §12.1).
 *
 * <p><b>Кто создаёт и кто потребляет.</b> В продуктивном коде создаётся только в
 * {@code ProcessService.createProcess} (эндпоинт создания процесса) и потребляется
 * {@code RouteGenerationService}: подбор шаблона, построение этапов/итераций/участников,
 * валидация и сохранение {@code ProcessInstance}.
 *
 * @param entityType     тип сущности, для которой создаётся процесс; переносится в
 *                       {@code ProcessInstance.entityType} и участвует в подборе шаблона
 * @param entityId       идентификатор сущности; переносится в {@code ProcessInstance.entityId}
 * @param templateId     явно выбранный шаблон; {@code null} означает <b>автоподбор</b> —
 *                       {@code MatchService.findMatchingTemplates} + {@code selectBestTemplate},
 *                       а при отсутствии подходящего шаблона бросается
 *                       {@link ru.coordination.approval.exception.NoMatchingTemplateException}.
 *                       Если идентификатор задан, но шаблона нет — бросается
 *                       {@link ru.coordination.approval.exception.TemplateNotFoundException}
 * @param organizationId организация, в которой резолвятся роли слотов; передаётся в
 *                       {@code RoleResolverAdapter.resolveRole} и в текущем вызове всегда
 *                       {@code null} ({@code ProcessService} его не заполняет)
 * @param initiatorId    инициатор процесса; переносится в {@code ProcessInstance.initiatorId}
 *                       и проверяется guard-ом {@code IsInitiatorGuard} при запуске процесса
 */
public record RouteGenerationRequest(
        String entityType,
        UUID entityId,
        UUID templateId, // nullable, for auto-selection
        UUID organizationId,
        UUID initiatorId
) {
}
