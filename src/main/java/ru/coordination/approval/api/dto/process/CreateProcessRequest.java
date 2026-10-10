package ru.coordination.approval.api.dto.process;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

/**
 * Тело запроса на создание процесса ({@code 07_api_contract.md} §12.1). Процесс создаётся в
 * статусе {@code Draft}: шаблон подбирается по паре «тип сущности + атрибуты», маршрут ещё не
 * запущен.
 *
 * @param entityType    тип внешней сущности (например {@code CONTRACT}); значение должно совпадать
 *                      с правилами применимости шаблона, иначе подходящий шаблон не найдётся и
 *                      вернётся {@code 404}
 * @param entitySubtype уточнение типа, участвует в подборе шаблона; может быть {@code null}
 * @param attributes    атрибуты снимка сущности для правил применимости; {@code @NotNull}, поэтому
 *                      «нет атрибутов» передаётся как пустой объект, а не как отсутствие поля
 * @param initiatorId   инициатор: записывается в {@code ProcessInstance.initiatorId} и получает
 *                      роль инициатора в маршруте
 */
public record CreateProcessRequest(
        @NotBlank String entityType,
        String entitySubtype,
        @NotNull UUID entityId,
        @NotNull Map<String, Object> attributes,
        @NotNull UUID initiatorId
) {
}
