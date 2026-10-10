package ru.coordination.approval.api.dto.decision;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Тело запроса решения участника ({@code 07_api_contract.md} §16.1).
 *
 * @param decision код из {@code decision_result_registry}: {@code APPROVE},
 *                 {@code APPROVE_WITH_COMMENTS}, {@code REJECT}; проверяется guard'ом
 *                 {@code IsValidDecisionType}
 * @param comment  текст решения; для {@code APPROVE_WITH_COMMENTS} обязателен по guard'у
 *                 {@code HasCommentWhenRequired} и сохраняется в таблицу {@code comment}
 * @param actorId  тот, от чьего имени принимается решение: значение не сверяется с участником из
 *                 пути, потому что аутентификации в этой фазе нет (открытый вопрос PHASE-21-25)
 */
public record DecideRequest(
        @NotBlank String decision,
        String comment,
        @NotNull UUID actorId
) {
}
