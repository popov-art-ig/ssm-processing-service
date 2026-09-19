package ru.coordination.approval.engine.registry;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.registry.GuardRegistryEntry;
import ru.coordination.approval.engine.ComponentResolver;
import ru.coordination.approval.engine.exception.UnknownGuardException;

/**
 * Резолвит код guard'а ({@code transition_config.guards[]}) в исполняемый бин через
 * {@code guard_registry.handler} (PHASE-02, раздел 4). Не обращается к
 * {@link org.springframework.context.ApplicationContext} напрямую — только через
 * {@link ComponentResolver}.
 */
@Component
@RequiredArgsConstructor
public class GuardRegistry {

    private final GuardRegistryRepository repository;
    private final ComponentResolver componentResolver;

    public Guard resolve(String code) {
        GuardRegistryEntry entry = repository.findById(code)
                .filter(GuardRegistryEntry::isActive)
                .orElseThrow(() -> new UnknownGuardException(code));
        return componentResolver.resolve(entry.getHandler(), Guard.class);
    }
}
