package ru.coordination.approval.engine.exception;

import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.statemachine.TriggerType;

/**
 * Нет ни одного активного {@code transition_config} для запрошенных
 * {@code (entityType, fromState, trigger)} — явное отсутствие конфигурации, а не
 * непройденные guards (PHASE-02, «Открытые вопросы» п.1). В отличие от «guards вернули
 * false», это ошибка использования/конфигурации: вызывающий код запросил переход, которого
 * в карте состояний не существует вообще.
 */
public class NoApplicableTransitionException extends RuntimeException {

    /**
     * @param entityType тип сущности, для которого запрошен переход
     * @param fromState  текущее состояние сущности на момент вызова
     * @param trigger    триггер, по которому переход искался, — в паре с {@code fromState} он и
     *                   составляет ключ, по которому не нашлось ни одной активной строки
     *                   {@code transition_config}
     */
    public NoApplicableTransitionException(EntityType entityType, String fromState, TriggerType trigger) {
        super("No active transition configured for entityType=%s, fromState=%s, trigger=%s"
                .formatted(entityType, fromState, trigger));
    }
}
