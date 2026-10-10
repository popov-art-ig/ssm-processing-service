package ru.coordination.approval.engine.registry;

import ru.coordination.approval.engine.TransitionContext;

/**
 * Предикат без побочных эффектов, проверяемый {@code TransitionEngine} перед выполнением
 * перехода (PHASE-02, раздел 4). Guards не могут менять состояние напрямую и должны быть
 * идемпотентны; выполняются в той же транзакции, что и {@code TransitionEngine}.
 */
@FunctionalInterface
public interface Guard {

    /**
     * {@code true} — условие перехода выполнено и {@code TransitionEngine} может перейти к
     * следующему guard'у или к actions. {@code false} отклоняет только этот переход: движок
     * пробует следующий кандидат по приоритету и не считает отказ ошибкой. Побочных эффектов
     * быть не должно — guard может вызываться для перехода, который в итоге не выполнится.
     */
    boolean evaluate(TransitionContext context);
}
