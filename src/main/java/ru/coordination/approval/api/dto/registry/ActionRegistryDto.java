package ru.coordination.approval.api.dto.registry;

/**
 * Запись реестра actions ({@code 07_api_contract.md} §24.3; структура записи —
 * {@code 05_guards_actions_registry.md} §3) — полный аналог {@link GuardRegistryDto} для действий
 * перехода.
 *
 * @param actionName  код action'а, он же значение в {@code transition_config.actions}
 * @param beanName    имя бина-реализации ({@code action_registry.handler})
 * @param description текст для администратора; {@code null}, если запись не описана
 */
public record ActionRegistryDto(
        String actionName,
        String beanName,
        String description
) {
}
