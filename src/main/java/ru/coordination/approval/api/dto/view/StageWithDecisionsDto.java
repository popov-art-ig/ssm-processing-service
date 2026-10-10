package ru.coordination.approval.api.dto.view;

import ru.coordination.approval.api.dto.process.StageDto;
import java.util.List;

/**
 * Этап вместе с плоским списком участников всех своих итераций и их решениями (ADR-020).
 *
 * <p>Отличие от «сырого» {@link ru.coordination.approval.api.dto.process.StageDto}: участники
 * подняты на уровень этапа, чтобы UI не разворачивал итерации, а решения уже подгружены. Цена —
 * теряется разделение по итерациям: участник прошлого круга согласования оказывается в том же
 * списке, что и текущий.
 */
public record StageWithDecisionsDto(
        StageDto stage,
        List<ParticipantWithDecisionDto> participants
) {
}
