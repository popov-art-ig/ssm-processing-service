package ru.coordination.approval.engine.registry;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.registry.ActionRegistryEntry;
import ru.coordination.approval.engine.ComponentResolver;
import ru.coordination.approval.engine.exception.UnknownActionException;

/**
 * Резолвит код action'а ({@code transition_config.actions[]}) в исполняемый бин через
 * {@code action_registry.handler} (PHASE-02, раздел 4). См. {@link GuardRegistry} за той же
 * логикой для guards.
 */
@Component
@RequiredArgsConstructor
public class ActionRegistry {

    private final ActionRegistryRepository repository;
    private final ComponentResolver componentResolver;

    public Action resolve(String code) {
        ActionRegistryEntry entry = repository.findById(code)
                .filter(ActionRegistryEntry::isActive)
                .orElseThrow(() -> new UnknownActionException(code));
        return componentResolver.resolve(entry.getHandler(), Action.class);
    }
}
