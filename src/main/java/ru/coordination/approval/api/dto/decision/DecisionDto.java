package ru.coordination.approval.api.dto.decision;

import java.time.Instant;
import java.util.UUID;

/**
 * Решение участника ({@code 07_api_contract.md} §16.1–16.2) — запись {@code decision} без
 * комментариев и вложений.
 *
 * @param decision  код результата {@code APPROVE}/{@code APPROVE_WITH_COMMENTS}/{@code REJECT};
 *                  имя поля расходится с {@code Decision.result}, из-за чего маппер делается
 *                  вручную
 * @param decidedAt момент записи решения (в сущности — {@code recordedAt}); по нему
 *                  восстанавливается хронология голосования
 * @param decidedBy всегда {@code null}: в {@code Decision} нет автора, он выводится из участника
 */
public record DecisionDto(
        UUID id,
        UUID participantId,
        String decision,
        String comment,
        Instant decidedAt,
        UUID decidedBy
) {
}
