package ru.coordination.approval.api.dto.statemachine;

import java.util.List;
import java.util.UUID;

/**
 * Переход карты ({@code 07_api_contract.md} §23.2) — декларативное описание, по которому движок
 * выбирает переход, а не его исполняемая логика.
 *
 * @param triggerType   тип триггера ({@code USER_ACTION}, {@code SYSTEM}, …): переход выполняется
 *                      только при совпадении триггера
 * @param guards        коды guard'ов в порядке приоритета; движок берёт первый переход, все guards
 *                      которого вернули {@code true}, поэтому порядок здесь значим
 * @param emittedEvents имена событий ({@code transition.emits}); публикация событий наружу —
 *                      отдельная фаза, сейчас список только читается
 */
public record TransitionConfigDto(
        UUID id,
        String transitionName,
        String fromState,
        String toState,
        String triggerType,
        List<String> guards,
        List<String> actions,
        List<String> emittedEvents
) {
}
