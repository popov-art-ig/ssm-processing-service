package ru.coordination.approval.exception;

/**
 * Ни один опубликованный шаблон не подошёл сущности: бросается в
 * {@code MatchService.selectBestTemplate}, когда {@code MatchService.findMatchingTemplates}
 * вернул пустой список, то есть автоподбор шаблона невозможен.
 *
 * <p>Слой — сервисный; путь вызова: {@code ProcessService.createProcess} →
 * {@code RouteGenerationService.generateRoute} при {@code RouteGenerationRequest.templateId == null}.
 * Ветки в {@code GlobalExceptionHandler} для этого исключения нет, поэтому его перехватывает общий
 * {@code @ExceptionHandler(Exception.class)} и клиент получает {@code 500 INTERNAL_ERROR} —
 * хотя ситуация для клиента исправима (нужно опубликовать подходящий шаблон), и ожидаемым был бы
 * код {@code 4xx}.
 */
public class NoMatchingTemplateException extends RuntimeException {
    /**
     * @param message описание причины для лога; клиенту этот текст не отдаётся — общий обработчик
     *                ошибок заменяет его обезличенным {@code INTERNAL_ERROR}
     */
    public NoMatchingTemplateException(String message) {
        super(message);
    }
}
