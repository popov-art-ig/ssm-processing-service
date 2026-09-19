package ru.coordination.approval.engine.model;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.coordination.approval.domain.statemachine.StateConfig;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.domain.statemachine.TransitionConfig;

/**
 * Строит {@link TransitionModel} по явно переданному id конкретной версии
 * {@link StateMachineConfig} (PHASE-02, раздел «3. ModelFactory»).
 *
 * <p>Не кэширует модель между вызовами — конфиг может быть опубликован заново под новым id;
 * фиксация версии конфига на конкретном процессе (Snapshot-on-Start) — ответственность
 * вызывающего кода в последующих фазах, не этого класса.
 */
@Component
@RequiredArgsConstructor
public class ModelFactory {

    private final StateMachineConfigRepository configRepository;
    private final StateConfigRepository stateConfigRepository;
    private final TransitionConfigRepository transitionConfigRepository;

    @Transactional(readOnly = true)
    public TransitionModel loadModel(UUID configId) {
        StateMachineConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "StateMachineConfig not found: " + configId));

        List<StateConfig> states = stateConfigRepository.findByConfigId(configId);
        List<TransitionConfig> transitions = transitionConfigRepository.findByConfigId(configId);

        Map<String, StateConfig> statesByCode = states.stream()
                .collect(Collectors.toMap(StateConfig::getCode, Function.identity()));

        Map<TransitionKey, List<TransitionConfig>> transitionsByKey = transitions.stream()
                .filter(TransitionConfig::isActive)
                .collect(Collectors.groupingBy(
                        t -> new TransitionKey(t.getFromState(), t.getTrigger())));

        transitionsByKey.values().forEach(candidates -> candidates.sort(
                Comparator.comparing(TransitionConfig::getPriority, Comparator.reverseOrder())
                        .thenComparing(TransitionConfig::getCode)));

        return new TransitionModel(config.getEntityType(), config.getId(), statesByCode, transitionsByKey);
    }
}
