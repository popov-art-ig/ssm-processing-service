package ru.coordination.approval.api.dto.registry;

/**
 * Запись реестра guards ({@code 07_api_contract.md} §24.1; структура записи —
 * {@code 05_guards_actions_registry.md} §3). Getter-имя {@code guardName} не совпадает с колонкой
 * {@code guard_registry.code}.
 *
 * @param guardName   код guard'а, он же значение в {@code transition_config.guards}
 * @param beanName    имя бина-реализации ({@code guard_registry.handler}), по которому движок
 *                    находит обработчик
 * @param description текст для администратора; {@code null}, если запись не описана
 */
public record GuardRegistryDto(
        String guardName,
        String beanName,
        String description
) {
}
