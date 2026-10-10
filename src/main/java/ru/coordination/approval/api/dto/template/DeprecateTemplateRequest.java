package ru.coordination.approval.api.dto.template;

import java.util.UUID;

/**
 * Тело запроса на снятие шаблона с публикации ({@code 07_api_contract.md} §10.7) — переход
 * {@code Published → Deprecated}.
 *
 * <p>От {@link PublishTemplateRequest} и {@link ArchiveTemplateRequest} отличается только смыслом
 * вызова: во всех трёх телах единственный необязательный {@code actorId}, поэтому DTO не смешивают
 * — иначе один и тот же тип означал бы три разные операции.
 *
 * @param actorId кто снимает шаблон с публикации; может быть {@code null}
 */
public record DeprecateTemplateRequest(
        UUID actorId
) {
}
