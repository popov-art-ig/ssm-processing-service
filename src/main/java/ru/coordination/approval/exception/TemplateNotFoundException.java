package ru.coordination.approval.exception;

import java.util.UUID;

/**
 * Шаблон не найден: бросается в {@code RouteGenerationService.selectTemplate}, когда
 * {@code RouteGenerationRequest.templateId} задан явно, но в {@code TemplateRepository} нет записи
 * с таким идентификатором (сервисный слой; путь вызова — {@code ProcessService.createProcess}).
 * Сообщение формируется конструктором в виде {@code "Template not found: " + templateId}.
 *
 * <p>Ветки в {@code GlobalExceptionHandler} для этого исключения нет, поэтому его перехватывает
 * общий {@code @ExceptionHandler(Exception.class)} и клиент получает {@code 500 INTERNAL_ERROR};
 * для того же смысла «сущность не найдена» в API есть отдельный тип
 * {@code api.exception.EntityNotFoundException}, который отдаёт {@code 404 NOT_FOUND}.
 */
public class TemplateNotFoundException extends RuntimeException {
    /**
     * @param templateId идентификатор запрошенного шаблона — подставляется в текст сообщения
     *                   {@code "Template not found: <id>"}, который попадает только в лог
     */
    public TemplateNotFoundException(UUID templateId) {
        super("Template not found: " + templateId);
    }
}
