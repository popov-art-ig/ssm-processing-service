package ru.coordination.approval.engine.registry;

import ru.coordination.approval.engine.TransitionContext;

/**
 * Операция с побочными эффектами, выполняемая {@code TransitionEngine} после успешной
 * проверки guards, строго по одной в порядке {@code transition_config.actions[]} (PHASE-02,
 * раздел 4). Может менять состояние сущностей и вызывать адаптеры (в этой фазе — только
 * заглушки); должна быть идемпотентна и выполняться в той же транзакции, что и
 * {@code TransitionEngine}.
 */
@FunctionalInterface
public interface Action {

    /**
     * Побочный эффект перехода: вызывается только для уже выбранного перехода, один раз и в
     * списке {@code actions[]}, то есть после записи нового {@code status} (см.
     * {@code TransitionEngine.execute}) — action, проверяющий состояние связанных сущностей,
     * видит статус текущей сущности уже изменённым. Возврата значения нет: неуспех action
     * выражается исключением, которое откатывает всю транзакцию перехода.
     */
    void execute(TransitionContext context);
}
