package ru.coordination.approval.api.dto.registry;

public record GuardRegistryDto(
        String guardName,
        String beanName,
        String description
) {
}
