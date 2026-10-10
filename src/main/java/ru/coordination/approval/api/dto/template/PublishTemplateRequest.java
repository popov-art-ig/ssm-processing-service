package ru.coordination.approval.api.dto.template;

import java.util.UUID;

/**
 * Тело запроса на публикацию шаблона ({@code 07_api_contract.md} §10.5) — переход
 * {@code Draft → Published} с проверкой структуры движком.
 *
 * @param actorId кто публикует; без {@code @NotNull}, поэтому тело {@code {}} допустимо, и тогда
 *                актор перехода в {@code audit_event} будет {@code null}
 */
public record PublishTemplateRequest(
        UUID actorId
) {
}
