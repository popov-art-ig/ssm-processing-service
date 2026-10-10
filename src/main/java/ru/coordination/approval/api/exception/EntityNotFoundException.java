package ru.coordination.approval.api.exception;

/**
 * Запрошенная сущность не найдена; обрабатывается {@link GlobalExceptionHandler} как
 * {@code 404 NOT_FOUND}. Бросается сервисным и API-слоем вместо возврата {@code null}.
 */
public class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String message) {
        super(message);
    }
}
