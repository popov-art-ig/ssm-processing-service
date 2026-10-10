package ru.coordination.approval.engine.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.statemachine.StateConfig;
import ru.coordination.approval.domain.statemachine.TransitionConfig;
import ru.coordination.approval.domain.statemachine.TriggerType;

/**
 * In-memory модель одной конкретной версии {@code StateMachineConfig}, построенная
 * {@link ModelFactory} — пригодна для быстрого поиска переходов по
 * {@code (fromState, trigger)} (шаг 1 алгоритма, PHASE-02).
 *
 * <p>Переходы внутри одного {@link TransitionKey} отсортированы по {@code priority} —
 * больший приоритет первым (шаг 2). Для равного {@code priority} порядок в спецификации не
 * задан; здесь используется детерминированный tie-break по {@code code} перехода
 * (лексикографически), чтобы результат не зависел от порядка чтения из БД.
 *
 * <p>Не кэшируется между вызовами {@link ModelFactory#loadModel(UUID)} — каждый вызов строит
 * новый снимок; какую версию конфига запросить (и, соответственно, продолжать ли работать по
 * старой версии после republish) решает вызывающий код (Snapshot-on-Start — вне объёма этой
 * фазы, см. тикет).
 *
 * @param entityType      тип сущности, для которого построена карта; {@code TransitionEngine}
 *                        сверяет его с запрошенным, чтобы переход не выполнился по чужой карте
 * @param configId        id версии конфига, по которой построена модель
 * @param statesByCode    состояния карты по коду; {@code TransitionEngine} их пока не читает
 *                        (переход выполняется по {@code toState} перехода, а существование
 *                        состояния проверяет валидатор) — поле модели остаётся заделом на работу
 *                        с {@code is_initial}/{@code is_terminal}/{@code metadata}
 * @param transitionsByKey активные переходы, сгруппированные по {@code (fromState, trigger)} и
 *                        отсортированные по приоритету
 */
public record TransitionModel(
        EntityType entityType,
        UUID configId,
        Map<String, StateConfig> statesByCode,
        Map<TransitionKey, List<TransitionConfig>> transitionsByKey) {

    /**
     * Кандидаты перехода для данных {@code fromState}/{@code trigger}, уже отсортированные по
     * приоритету (первый в списке — обрабатывается первым, шаг 2 алгоритма). Пустой список,
     * если подходящих активных переходов нет вообще.
     */
    public List<TransitionConfig> candidates(String fromState, TriggerType trigger) {
        return transitionsByKey.getOrDefault(new TransitionKey(fromState, trigger), List.of());
    }
}
