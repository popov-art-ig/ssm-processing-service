package ru.coordination.approval.adapter;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.coordination.approval.dto.generation.EntitySnapshot;

/**
 * Пустая реализация {@link RoleResolverAdapter}: внешний справочник ролей не подключён, поэтому
 * резолвинг всегда возвращает пустой список — «пользователь не найден» в терминах
 * {@code 09_adapters.md} §5.5.
 *
 * <p><b>Это боевой бин, а не тестовый двойник.</b> Класс помечен {@code @Component} и является
 * единственной реализацией {@link RoleResolverAdapter} в репозитории, поэтому Spring подставляет
 * его и в проде; реальная реализация ({@code 09_adapters.md} §5.6, включается через
 * {@code @ConditionalOnProperty}) не написана.
 *
 * <p><b>Что это значит на практике.</b> Слот шаблона, у которого заполнены только роли
 * ({@code SlotTemplate.acceptableRoles}), не получает ни одного участника:
 * {@code RouteGenerationService.resolveSlot} возвращает пустой список. Если такой слот объявлен
 * обязательным, {@code RouteValidatorService} при генерации маршрута бросает
 * {@code RequiredSlotNotResolvedException} — процесс по такому шаблону создать нельзя до
 * подключения реального адаптера. Слот с явно заданным {@code userId} от адаптера не зависит и
 * резолвится напрямую.
 *
 * <p><b>Не документировано в коде:</b> комментарий в теле метода ссылается на {@code PHASE-20},
 * однако тикета с таким номером в {@code doc/tickets/} нет — проверить срок подключения реальной
 * реализации по репозиторию невозможно.
 */
@Component
public class RoleResolverAdapterStub implements RoleResolverAdapter {

    /**
     * @return всегда пустой список: резолвинг роли не выполняется (см. описание класса)
     */
    @Override
    public List<UUID> resolveRole(UUID roleId, UUID organizationId, EntitySnapshot context) {
        // Stub: always returns empty list
        // Real implementation will be in PHASE-20
        return List.of();
    }
}
