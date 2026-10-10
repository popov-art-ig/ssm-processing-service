package ru.coordination.approval.adapter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.coordination.approval.dto.generation.EntitySnapshot;

/**
 * Заглушка вместо реального {@link EntityAdapter}: система-владелец сущности не подключена,
 * поэтому снимок всегда собирается из фиксированных значений ({@code amount = 1000000},
 * {@code priority = "HIGH"}) независимо от {@code entityType} и {@code entityId}, а
 * {@code entitySubtype} равен {@code null}.
 *
 * <p><b>Что подменяется.</b> Реальный адаптер — REST-вызов к системе-владельцу сущности
 * ({@code 09_adapters.md} §6.5) — должен вернуть фактические атрибуты конкретной сущности.
 * Заглушка отдаёт одни и те же данные для любого идентификатора, поэтому правила применимости
 * с условиями на атрибуты ({@code applicability_rule.attribute_conditions}) срабатывают
 * одинаково для всех сущностей, и подбор шаблона перестаёт зависеть от данных.
 *
 * <p><b>Почему это критично в проде.</b> Класс помечен {@code @Component} и является
 * единственной реализацией {@link EntityAdapter} в репозитории, то есть Spring внедряет именно
 * его — вопреки {@code 09_adapters.md} §6.5 и {@code 10_architecture.md} §8.3, где
 * {@code EntityAdapter} назван обязательным реальным адаптером без пустой реализации.
 *
 * <p><b>След в коде, вызванный заглушкой.</b> {@code ProcessService.createProcess} сознательно
 * не вызывает {@code EntityAdapter}: снимок {@link EntitySnapshot} собирается из тела запроса
 * ({@code CreateProcessRequest.attributes}) и передаётся в перегрузку
 * {@code RouteGenerationService.generateRoute(request, snapshot)}. Причина названа в комментарии
 * {@code ProcessService} прямо — иначе заглушка подменила бы атрибуты клиента фиксированными.
 * Как следствие, путь «снимок из адаптера» (перегрузка
 * {@code RouteGenerationService.generateRoute(request)}) в проде даёт фиктивные атрибуты и
 * всегда {@code null} в подтипе сущности.
 */
@Component
public class EntityAdapterStub implements EntityAdapter {

    /**
     * Всегда возвращает снимок с фиксированными атрибутами, {@code entitySubtype = null} и
     * {@code fetchedAt = Instant.now()}.
     */
    @Override
    public EntitySnapshot fetchEntitySnapshot(String entityType, UUID entityId) {
        // Stub for tests
        return new EntitySnapshot(
                entityType,
                null,
                entityId,
                Map.of("amount", 1000000, "priority", "HIGH"),
                Instant.now()
        );
    }
}
