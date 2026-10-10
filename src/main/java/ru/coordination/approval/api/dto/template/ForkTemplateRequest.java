package ru.coordination.approval.api.dto.template;

import java.util.UUID;

/**
 * Тело запроса на форк шаблона ({@code 07_api_contract.md} §10.6).
 *
 * @param actorId кто выполняет форк; используется как {@code createdBy} новой версии
 */
public record ForkTemplateRequest(
        UUID actorId
) {
}
