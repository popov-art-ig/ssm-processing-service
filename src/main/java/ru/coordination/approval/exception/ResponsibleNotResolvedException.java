package ru.coordination.approval.exception;

/**
 * <b>Объявлено, но нигде не бросается и не перехватывается:</b> в репозитории нет ни одного
 * {@code new ResponsibleNotResolvedException(...)} и ни одного {@code catch} на этот тип — ни в
 * {@code src/main}, ни в {@code src/test}.
 *
 * <p>Назначение в документации не зафиксировано. Имя указывает на нерезолвленного
 * «ответственного» процесса ({@code 01_glossary.md} §17.3; поле
 * {@code ProcessInstance.responsibleUserId}), однако это поле ни один сервис не заполняет,
 * поэтому проверка, для которой исключение предположительно объявлялось, в текущем объёме не
 * реализована.
 */
public class ResponsibleNotResolvedException extends RuntimeException {
    /**
     * @param message описание причины; поскольку исключение нигде не бросается, содержимое
     *                сообщения в текущем коде не проверяется
     */
    public ResponsibleNotResolvedException(String message) {
        super(message);
    }
}
