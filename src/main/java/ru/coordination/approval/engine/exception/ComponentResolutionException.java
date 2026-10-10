package ru.coordination.approval.engine.exception;

/**
 * {@code guard_registry.handler}/{@code action_registry.handler} не резолвится ни как имя
 * Spring-бина, ни как полное имя класса с зарегистрированным бином этого типа
 * ({@link ru.coordination.approval.engine.ComponentResolver}).
 */
public class ComponentResolutionException extends RuntimeException {

    /**
     * @param handler      значение {@code guard_registry.handler}/{@code action_registry.handler}:
     *                     имя бина или полное имя класса, которое не удалось разрешить
     * @param expectedType тип, к которому бин должен был приводиться ({@code Guard} или
     *                     {@code Action}) — попадает в сообщение, потому что одно и то же имя
     *                     может существовать в контексте бином другого типа
     * @param cause        исходная причина (нет бина, нет класса, несовпадение типа)
     */
    public ComponentResolutionException(String handler, Class<?> expectedType, Throwable cause) {
        super("Cannot resolve handler '%s' to a Spring bean of type %s"
                .formatted(handler, expectedType.getName()), cause);
    }
}
