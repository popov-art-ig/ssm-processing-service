package ru.coordination.approval.engine;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import ru.coordination.approval.engine.exception.ComponentResolutionException;

/**
 * Тонкая обёртка над {@link ApplicationContext}, резолвящая {@code handler} из
 * {@code guard_registry}/{@code action_registry} в конкретный Spring-бин (PHASE-02, раздел
 * 5). Используется только {@code GuardRegistry}/{@code ActionRegistry} — {@code
 * TransitionEngine} к {@link ApplicationContext} напрямую не обращается.
 *
 * <p>{@code handler} резолвится сначала как имя бина, затем — если это не сработало — как
 * полное имя класса, для которого ищется бин этого типа.
 */
@Component
@RequiredArgsConstructor
public class ComponentResolver {

    private final ApplicationContext applicationContext;

    /**
     * Резолвит {@code handler} в бин ожидаемого типа.
     *
     * <p>Сначала {@code handler} трактуется как имя бина — так записаны все сид-строки
     * {@code guard_registry}/{@code action_registry} (например {@code 'isInitiatorGuard'},
     * миграции V18–V25). Полное имя класса — запасной вариант для записей, где указан класс:
     * он ищется через {@code Class.forName}, поэтому опечатка в handler'е не должна выглядеть
     * как «бинов нет вообще».
     *
     * @throws ComponentResolutionException если handler не разрешился ни одним из двух способов:
     *         нет бина с таким именем, нет класса с таким именем или бин второго способа не
     *         приводится к ожидаемому типу. Известное ограничение: бин, найденный по имени, но
     *         другого типа, приводит к {@code BeanNotOfRequiredTypeException} от Spring — она не
     *         оборачивается (ловится только отсутствие бина) и уходит вызывающему коду как есть
     */
    public <T> T resolve(String handler, Class<T> expectedType) {
        try {
            return applicationContext.getBean(handler, expectedType);
        } catch (NoSuchBeanDefinitionException byName) {
            try {
                Class<?> handlerClass = Class.forName(handler);
                Object bean = applicationContext.getBean(handlerClass);
                return expectedType.cast(bean);
            } catch (ClassNotFoundException | NoSuchBeanDefinitionException | ClassCastException e) {
                throw new ComponentResolutionException(handler, expectedType, e);
            }
        }
    }
}
