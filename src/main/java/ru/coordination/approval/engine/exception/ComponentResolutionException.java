package ru.coordination.approval.engine.exception;

/**
 * {@code guard_registry.handler}/{@code action_registry.handler} не резолвится ни как имя
 * Spring-бина, ни как полное имя класса с зарегистрированным бином этого типа
 * ({@link ru.coordination.approval.engine.ComponentResolver}).
 */
public class ComponentResolutionException extends RuntimeException {

    public ComponentResolutionException(String handler, Class<?> expectedType, Throwable cause) {
        super("Cannot resolve handler '%s' to a Spring bean of type %s"
                .formatted(handler, expectedType.getName()), cause);
    }
}
