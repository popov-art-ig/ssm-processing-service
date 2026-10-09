package ru.coordination.approval.api.exception;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.coordination.approval.api.dto.ErrorResponse;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Ошибки декларативной валидации ({@code @Valid} на теле запроса).
     *
     * <p>Без этого обработчика {@link MethodArgumentNotValidException} перехватывался
     * {@link #handleInternalError} и клиент получал {@code 500 INTERNAL_ERROR} вместо
     * {@code 400 VALIDATION_ERROR} (находка сборки: тесты на некорректное тело падали с
     * ожиданием 400, фактически 500).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> "%s: %s".formatted(error.getField(), error.getDefaultMessage()))
                .toList();

        String message = details.isEmpty()
                ? "Request validation failed"
                : "Request validation failed: " + String.join("; ", details);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("BAD_REQUEST", ex.getMessage()));
    }

    /**
     * Ошибки доступа к данным, в которых Spring/Hibernate обернули ошибку приложения.
     *
     * <p>Действия карты переходов бросают {@code IllegalArgumentException} как сигнал о неверном
     * запросе — например {@code ValidateTemplateStructureAction} отказывает в публикации шаблона
     * без обязательных полей этапа. Внутри транзакции Spring переводит такое исключение в
     * {@code InvalidDataAccessApiUsageException}, и без этой ветки клиент получал бы 500 вместо
     * 400 с описанием проблемы.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException ex) {
        IllegalArgumentException cause =
                ExceptionCauses.findCause(ex, IllegalArgumentException.class);

        if (cause != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("BAD_REQUEST", cause.getMessage()));
        }

        log.error("Unhandled data access exception while processing request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "A data access error occurred"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONFLICT", ex.getMessage()));
    }

    /**
     * Последний рубеж: клиент получает обезличенный ответ, но причина логируется — иначе
     * диагностика 500-х ответов невозможна (тело ответа намеренно не раскрывает деталей).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleInternalError(Exception ex) {
        log.error("Unhandled exception while processing request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
