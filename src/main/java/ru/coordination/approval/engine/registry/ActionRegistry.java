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

    /**
     * Резолвит код action'а в бин на каждый вызов перехода; {@code isActive} проверяется
     * повторно по тем же причинам, что и в {@link GuardRegistry} — запись реестра может быть
     * снята уже после того, как карта прошла валидацию.
     *
     * @throws UnknownActionException если запись отсутствует или неактивна
     * @throws ru.coordination.approval.engine.exception.ComponentResolutionException если
     *         {@code handler} записи не резолвится в бин
     */
    public Action resolve(String code) {
        ActionRegistryEntry entry = repository.findById(code)
                .filter(ActionRegistryEntry::isActive)
                .orElseThrow(() -> new UnknownActionException(code));
        return componentResolver.resolve(entry.getHandler(), Action.class);
    }
}
