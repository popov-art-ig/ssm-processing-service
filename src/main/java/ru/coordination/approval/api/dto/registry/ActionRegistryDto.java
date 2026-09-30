package ru.coordination.approval.api.dto.registry;

public record ActionRegistryDto(
        String actionName,
        String beanName,
        String description
) {
}
