package ru.coordination.approval.engine.registry;

import ru.coordination.approval.engine.TransitionContext;

/**
 * Предикат без побочных эффектов, проверяемый {@code TransitionEngine} перед выполнением
 * перехода (PHASE-02, раздел 4). Guards не могут менять состояние напрямую и должны быть
 * идемпотентны; выполняются в той же транзакции, что и {@code TransitionEngine}.
 */
@FunctionalInterface
public interface Guard {

    boolean evaluate(TransitionContext context);
}
