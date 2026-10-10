package ru.coordination.approval.engine.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.registry.ActionRegistryEntry;
import ru.coordination.approval.domain.registry.GuardRegistryEntry;
import ru.coordination.approval.domain.statemachine.StateConfig;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.domain.statemachine.TransitionConfig;
import ru.coordination.approval.engine.model.StateConfigRepository;
import ru.coordination.approval.engine.model.StateMachineConfigRepository;
import ru.coordination.approval.engine.model.TransitionConfigRepository;
import ru.coordination.approval.engine.registry.ActionRegistryRepository;
import ru.coordination.approval.engine.registry.GuardRegistryRepository;

/**
 * Валидация карты переходов перед публикацией ({@code 05_guards_actions_registry.md} §7.1).
 *
 * <p>Проверки спецификации: наличие каждого guard/action в реестре, известность каждого
 * {@code emits}, соответствие параметров схеме {@code params_schema}. Дополнительно проверяются
 * структурные инварианты, отсутствие которых в спецификации привело к реальным дефектам
 * (см. {@link TransitionMapErrorCode}) — в частности неоднозначный выбор перехода из одной пары
 * {@code (from_state, trigger)} и неприменимость guard/action к типу сущности.
 *
 * <p>Нарушения не бросаются по одному: {@link #validate} возвращает полный список, а
 * {@link #validateOrThrow} превращает его в {@link InvalidTransitionMapException}. Так при
 * публикации видно сразу все проблемы карты.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TransitionMapValidator {

    private final StateMachineConfigRepository stateMachineConfigRepository;
    private final StateConfigRepository stateConfigRepository;
    private final TransitionConfigRepository transitionConfigRepository;
    private final GuardRegistryRepository guardRegistryRepository;
    private final ActionRegistryRepository actionRegistryRepository;

    /**
     * Проверяет карту, загружая её состояния и переходы.
     *
     * @return список нарушений; пустой список — карта корректна
     */
    @Transactional(readOnly = true)
    public List<TransitionMapViolation> validate(StateMachineConfig config) {
        return validate(
                config,
                stateConfigRepository.findByConfigId(config.getId()),
                transitionConfigRepository.findByConfigId(config.getId()));
    }

    /**
     * Проверяет переданные состояния и переходы. Отдельная перегрузка нужна, чтобы валидацию
     * можно было применять к ещё не сохранённой карте (публикация новой версии) и к данным,
     * собранным в памяти.
     */
    public List<TransitionMapViolation> validate(
            StateMachineConfig config,
            List<StateConfig> states,
            List<TransitionConfig> transitions) {

        List<TransitionMapViolation> violations = new ArrayList<>();
        String entityTypeName = config.getEntityType().name();

        Map<String, GuardRegistryEntry> guards = loadGuards();
        Map<String, ActionRegistryEntry> actions = loadActions();

        Set<String> stateCodes = new HashSet<>();
        int initialStates = 0;
        for (StateConfig state : states) {
            stateCodes.add(state.getCode());
            if (state.isInitial()) {
                initialStates++;
            }
        }

        if (states.isEmpty()) {
            violations.add(TransitionMapViolation.of(
                    TransitionMapErrorCode.STATE_UNKNOWN, describe(config),
                    "карта не содержит ни одного состояния"));
        } else if (initialStates == 0) {
            violations.add(TransitionMapViolation.of(
                    TransitionMapErrorCode.INITIAL_STATE_MISSING, describe(config),
                    "ни одно состояние не помечено is_initial — движку неоткуда начать"));
        } else if (initialStates > 1) {
            violations.add(TransitionMapViolation.of(
                    TransitionMapErrorCode.MULTIPLE_INITIAL_STATES, describe(config),
                    "начальных состояний %d, ожидается ровно одно".formatted(initialStates)));
        }

        if (transitions.isEmpty()) {
            violations.add(TransitionMapViolation.of(
                    TransitionMapErrorCode.STATE_UNKNOWN, describe(config),
                    "карта не содержит ни одного перехода"));
        }

        // (from_state, trigger, guards) -> to_state первого активного перехода
        Map<String, String> activeTargets = new HashMap<>();
        Set<String> seenCodes = new HashSet<>();

        for (TransitionConfig transition : transitions) {
            String where = "transition '%s'".formatted(transition.getCode());

            if (!stateCodes.contains(transition.getFromState())) {
                violations.add(TransitionMapViolation.of(
                        TransitionMapErrorCode.STATE_UNKNOWN, where,
                        "from_state '%s' отсутствует среди состояний карты"
                                .formatted(transition.getFromState())));
            }
            if (!stateCodes.contains(transition.getToState())) {
                violations.add(TransitionMapViolation.of(
                        TransitionMapErrorCode.STATE_UNKNOWN, where,
                        "to_state '%s' отсутствует среди состояний карты"
                                .formatted(transition.getToState())));
            }

            // Нарушение уникальности (config_id, code, from_state): в БД его ловит уникальный
            // индекс, но при публикации карты в память её нужно проверить до записи.
            String codeKey = transition.getCode() + "@" + transition.getFromState();
            if (!seenCodes.add(codeKey)) {
                violations.add(TransitionMapViolation.of(
                        TransitionMapErrorCode.DUPLICATE_TRANSITION_CODE, where,
                        "код '%s' уже объявлен для from_state '%s'"
                                .formatted(transition.getCode(), transition.getFromState())));
            }

            for (String guardCode : transition.getGuards()) {
                GuardRegistryEntry guard = guards.get(guardCode);
                if (guard == null || !guard.isActive()) {
                    violations.add(TransitionMapViolation.of(
                            TransitionMapErrorCode.GUARD_NOT_FOUND, where,
                            "guard '%s' не найден в guard_registry или неактивен".formatted(guardCode)));
                } else if (!appliesTo(guard.getApplicableEntities(), entityTypeName)) {
                    violations.add(TransitionMapViolation.of(
                            TransitionMapErrorCode.APPLICABILITY_MISMATCH, where,
                            "guard '%s' объявлен для %s, а карта — для %s"
                                    .formatted(guardCode, guard.getApplicableEntities(), entityTypeName)));
                }
            }

            for (String actionCode : transition.getActions()) {
                ActionRegistryEntry action = actions.get(actionCode);
                if (action == null || !action.isActive()) {
                    violations.add(TransitionMapViolation.of(
                            TransitionMapErrorCode.ACTION_NOT_FOUND, where,
                            "action '%s' не найден в action_registry или неактивен".formatted(actionCode)));
                } else if (!appliesTo(action.getApplicableEntities(), entityTypeName)) {
                    violations.add(TransitionMapViolation.of(
                            TransitionMapErrorCode.APPLICABILITY_MISMATCH, where,
                            "action '%s' объявлен для %s, а карта — для %s"
                                    .formatted(actionCode, action.getApplicableEntities(), entityTypeName)));
                }
            }

            for (String event : transition.getEmits()) {
                if (!KnownEventTypes.isKnown(event)) {
                    violations.add(TransitionMapViolation.of(
                            TransitionMapErrorCode.EVENT_UNKNOWN, where,
                            "событие '%s' отсутствует в контракте событий (06_event_contract.md §18)"
                                    .formatted(event)));
                }
            }

            if (transition.isActive()) {
                // Ключ включает набор guards: неоднозначность — это когда один и тот же
                // (from_state, trigger) ведёт в РАЗНЫЕ to_state при ОДИНАКОВЫХ условиях. Тогда
                // выбор исхода не основан ни на условиях, ни на данных — только на priority и
                // лексикографическом порядке кодов, то есть на случайности конфигурации. Именно
                // такой набор давал «rejectRemark → Processed» и «возобновление → отзыв».
                //
                // Разные guards — осознанное конфигурирование: priority задаёт, какой исход
                // проверяется раньше (например «с замечаниями» раньше обычного «одобрено»).
                String guardsKey = String.join(",", transition.getGuards());
                String key = transition.getFromState() + "|" + transition.getTrigger() + "|" + guardsKey;

                String previousTarget = activeTargets.putIfAbsent(key, transition.getToState());
                if (previousTarget != null && !previousTarget.equals(transition.getToState())) {
                    violations.add(TransitionMapViolation.of(
                            TransitionMapErrorCode.AMBIGUOUS_TRANSITION, where,
                            "из состояния '%s' по %s уже есть активный переход в '%s' с тем же набором "
                                    + "guards [%s], а этот ведёт в '%s'. Выбор исхода будет зависеть от "
                                    + "priority и лексикографического порядка кодов, а не от условий — "
                                    + "разведите исходы разными guards или задайте явный priority"
                                    .formatted(transition.getFromState(), transition.getTrigger(),
                                            previousTarget, guardsKey, transition.getToState())));
                }
            }
        }

        return violations;
    }

    /** Проверяет карту и бросает {@link InvalidTransitionMapException}, если есть нарушения. */
    @Transactional(readOnly = true)
    public void validateOrThrow(StateMachineConfig config) {
        List<TransitionMapViolation> violations = validate(config);
        if (!violations.isEmpty()) {
            throw new InvalidTransitionMapException(describe(config), violations);
        }
    }

    /**
     * Проверяет только ОПУБЛИКОВАННЫЕ карты — те, что реально исполняются движком.
     *
     * <p>Черновики исключены осознанно: карта в статусе {@code DRAFT} может быть неполной, и
     * именно на этапе публикации {@link #validateOrThrow} должен не дать ей стать
     * {@code PUBLISHED}. Проверка на старте приложения (см.
     * {@code TransitionMapStartupValidator}) использует этот метод.
     */
    @Transactional(readOnly = true)
    public List<TransitionMapViolation> validatePublished() {
        List<TransitionMapViolation> violations = new ArrayList<>();
        for (StateMachineConfig config : stateMachineConfigRepository.findAll()) {
            if (config.getStatus() != LifecycleStatus.PUBLISHED) {
                continue;
            }
            violations.addAll(validate(config));
        }
        return violations;
    }

    private Map<String, GuardRegistryEntry> loadGuards() {
        Map<String, GuardRegistryEntry> byCode = new HashMap<>();
        for (GuardRegistryEntry entry : guardRegistryRepository.findAll()) {
            byCode.put(entry.getCode(), entry);
        }
        return byCode;
    }

    private Map<String, ActionRegistryEntry> loadActions() {
        Map<String, ActionRegistryEntry> byCode = new HashMap<>();
        for (ActionRegistryEntry entry : actionRegistryRepository.findAll()) {
            byCode.put(entry.getCode(), entry);
        }
        return byCode;
    }

    /**
     * Проверяет применимость записи реестра к типу сущности.
     *
     * <p>Пустой {@code applicableEntities} трактуется как «применимо везде» — так записи,
     * заведённые до появления этого измерения, не блокируют публикацию.
     */
    private boolean appliesTo(List<String> applicableEntities, String entityTypeName) {
        return applicableEntities == null
                || applicableEntities.isEmpty()
                || applicableEntities.contains(entityTypeName);
    }

    private String describe(StateMachineConfig config) {
        return "%s/%s v%d (id=%s)".formatted(
                config.getEntityType(), config.getProcessType(), config.getVersion(), config.getId());
    }
}
