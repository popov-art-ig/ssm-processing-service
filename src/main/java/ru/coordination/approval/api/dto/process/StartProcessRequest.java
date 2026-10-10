package ru.coordination.approval.api.dto.process;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Тело запроса запуска процесса ({@code 07_api_contract.md} §12.4) — переход
 * {@code Draft → InProgress}.
 *
 * @param actorId кто выполняет запуск; передаётся в {@code TransitionContext} и попадает в
 *                {@code audit_event}, но на принадлежность инициатору не проверяется — аутентификации
 *                в этой фазе нет
 */
public record StartProcessRequest(
        @NotNull UUID actorId
) {
}
