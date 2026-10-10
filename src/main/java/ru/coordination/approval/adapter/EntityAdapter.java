package ru.coordination.approval.adapter;

import java.util.UUID;
import ru.coordination.approval.dto.generation.EntitySnapshot;

/**
 * Порт получения снимка сущности из системы-владельца: атрибуты сущности — входные данные для
 * подбора шаблона по правилам применимости (ADR-004; {@code 09_adapters.md} §6.1,
 * {@code 10_architecture.md} §8.2).
 *
 * <p>Единственный адаптер, для которого спецификация требует <b>реальную</b> реализацию и не
 * допускает пустой: без снимка сущности подбор шаблона невозможен, поэтому при недоступности
 * внешней системы контракт предписывает бросить исключение, а не вернуть безопасное значение
 * ({@code 09_adapters.md} §6.5–6.6, §9.1; {@code 10_architecture.md} §8.3).
 *
 * <p><b>Отклонения от спецификации (зафиксированы как есть, не исправлялись).</b> Сигнатура
 * отличается от контракта {@code 09_adapters.md} §6.3: вместо {@code getSnapshot(EntityRef)} и
 * {@code exists(EntityRef)} объявлен один метод {@code fetchEntitySnapshot(entityType, entityId)},
 * а тип возврата не предусматривает {@code null} («сущность не найдена» в спецификации).
 * Единственная реализация в репозитории — {@link EntityAdapterStub}, то есть вместо требуемого
 * реального адаптера подключена заглушка.
 */
public interface EntityAdapter {

    /**
     * @param entityType тип сущности во внешней системе (значение {@code ProcessInstance.entityType})
     * @param entityId   идентификатор сущности во внешней системе
     * @return снимок сущности для подбора шаблона; {@code null} текущим контрактом не предусмотрен
     */
    EntitySnapshot fetchEntitySnapshot(String entityType, UUID entityId);
}
