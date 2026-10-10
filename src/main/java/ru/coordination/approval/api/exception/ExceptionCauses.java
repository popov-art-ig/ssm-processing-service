package ru.coordination.approval.api.exception;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;

/**
 * Извлекает исходную причину из исключений, которыми Spring/Hibernate оборачивают ошибки
 * приложения.
 *
 * <p>Зачем: действия карты переходов бросают {@code IllegalArgumentException} как сигнал
 * «запрос неверен» (например {@code ValidateTemplateStructureAction} при публикации шаблона).
 * Если исключение вылетает внутри транзакции, {@code EntityManagerFactoryUtils} переводит его в
 * {@link InvalidDataAccessApiUsageException} (наследник {@link DataAccessException}), и
 * {@code @ExceptionHandler(IllegalArgumentException.class)} его уже не ловит — клиент получает
 * 500 вместо 400 с описанием, что именно не так в шаблоне.
 */
final class ExceptionCauses {

    /**
     * Предел обхода цепочки {@code getCause()}: защита от зацикливания, если исключения
     * ссылаются друг на друга. Глубже этого уровня причина не ищется — метод вернёт {@code null},
     * и {@link GlobalExceptionHandler} отдаст {@code 500} вместо {@code 400}.
     */
    private static final int MAX_DEPTH = 10;

    private ExceptionCauses() {
    }

    /**
     * Удобная обёртка над {@link #findCause}: отвечает, есть ли в цепочке причин
     * {@link IllegalArgumentException}.
     *
     * <p><b>Не используется:</b> {@link GlobalExceptionHandler#handleDataAccess} вызывает
     * {@link #findCause} напрямую, потому что ему нужен сам объект причины — из него берётся
     * сообщение для клиента. Метод оставлен как точка расширения, но при ревью его стоит либо
     * задействовать, либо удалить.
     *
     * @return {@code true}, если в цепочке причин есть {@code IllegalArgumentException}
     */
    static boolean hasIllegalArgumentCause(Throwable throwable) {
        return findCause(throwable, IllegalArgumentException.class) != null;
    }

    /**
     * Ищет причину заданного типа в цепочке {@code getCause()}.
     *
     * @param type целевой тип
     * @return первая подходящая причина или {@code null}
     */
    static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++) {
            if (type.isInstance(current) && current != throwable) {
                return type.cast(current);
            }
            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
        }
        return null;
    }
}
