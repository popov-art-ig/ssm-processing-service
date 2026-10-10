package ru.coordination.approval.exception;

/**
 * Обязательный слот шаблона остался без участника: бросается в {@code RouteValidatorService} при
 * генерации маршрута — либо в структурном режиме (без этапов шаблона), когда у этапа нет ни одного
 * участника, либо когда у слота с {@code SlotTemplate.isRequired() == true} участников не оказалось.
 *
 * <p>Слой — сервисный; путь вызова: {@code ProcessService.createProcess} →
 * {@code RouteGenerationService.generateRoute} → {@code RouteValidatorService.validate}. Ветки в
 * {@code GlobalExceptionHandler} нет: исключение перехватывает общий
 * {@code @ExceptionHandler(Exception.class)} и превращается в {@code 500 INTERNAL_ERROR}, хотя
 * причина — незаполненный обязательный слот маршрута.
 */
public class RequiredSlotNotResolvedException extends RuntimeException {
    /**
     * @param message описание причины для лога; клиенту этот текст не отдаётся — общий обработчик
     *                ошибок заменяет его обезличенным {@code INTERNAL_ERROR}
     */
    public RequiredSlotNotResolvedException(String message) {
        super(message);
    }
}
