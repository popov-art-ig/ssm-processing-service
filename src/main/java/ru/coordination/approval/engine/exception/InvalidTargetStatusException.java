package ru.coordination.approval.engine.exception;

import ru.coordination.approval.domain.common.EntityType;

/**
 * {@code toState} перехода отсутствует в {@code status_registry} для данного
 * {@code entityType} — защита от рассинхронизации {@code transition_config} и реестра
 * статусов (PHASE-02, раздел 6 «TransitionEngine»). Ошибка конфигурации, а не тихая запись
 * невалидного статуса.
 */
public class InvalidTargetStatusException extends RuntimeException {

    /**
     * @param entityType тип сущности, для которого проверяется статус ({@code status_registry}
     *                   хранит его строкой, поэтому в сообщении он приводится через {@code name()})
     * @param toState    целевое состояние перехода, отсутствующее в реестре статусов
     */
    public InvalidTargetStatusException(EntityType entityType, String toState) {
        super("toState '%s' is not registered in status_registry for entityType=%s"
                .formatted(toState, entityType));
    }
}
