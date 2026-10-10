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

    /**
     * Резолвит код guard'а в бин на каждый вызов перехода — бин не кэшируется, поэтому
     * деактивированная запись {@code guard_registry} перестаёт исполняться без перезапуска
     * сервиса (кэш продолжал бы вызывать снятый guard).
     *
     * <p>{@code isActive} проверяется здесь повторно, хотя то же условие проверяет
     * {@link ru.coordination.approval.engine.validation.TransitionMapValidator} при публикации и
     * на старте: валидатор видит карту на момент проверки, а {@code is_active} может стать
     * {@code false} позже. Неактивная запись трактуется как отсутствующая — исполнять guard,
     * снятый с реестра, опаснее, чем отказать в переходе.
     *
     * @throws UnknownGuardException если запись отсутствует или неактивна
     * @throws ru.coordination.approval.engine.exception.ComponentResolutionException если
     *         {@code handler} записи не резолвится в бин
     */
    public Guard resolve(String code) {
        GuardRegistryEntry entry = repository.findById(code)
                .filter(GuardRegistryEntry::isActive)
                .orElseThrow(() -> new UnknownGuardException(code));
        return componentResolver.resolve(entry.getHandler(), Guard.class);
    }
}
