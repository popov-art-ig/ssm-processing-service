package ru.coordination.approval.api.dto.view;

import ru.coordination.approval.api.dto.decision.DecisionDto;
import ru.coordination.approval.api.dto.process.ParticipantDto;

/**
 * Пара «участник + его решение» для агрегированного экрана (ADR-020).
 *
 * @param decision {@code null}, если участник ещё не голосовал: пары собираются по карте
 *                 «participantId → Decision», и отсутствие записи — штатное состояние, а не
 *                 ошибка данных
 */
public record ParticipantWithDecisionDto(
        ParticipantDto participant,
        DecisionDto decision
) {
}
