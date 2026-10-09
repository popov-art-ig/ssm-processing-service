package ru.coordination.approval.engine.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.domain.statemachine.TransitionConfig;
import ru.coordination.approval.engine.model.StateConfigRepository;
import ru.coordination.approval.engine.model.StateMachineConfigRepository;
import ru.coordination.approval.engine.model.TransitionConfigRepository;

/**
 * Проверка валидатора карты переходов на РЕАЛЬНЫХ картах из миграций Flyway (Testcontainers).
 *
 * <p>Это регрессионный тест на дефекты, найденные аудитом 2026-10-08: все они были дефектами
 * данных карты (несуществующие ссылки, неприменимые guard/action, события вне контракта,
 * неоднозначный выбор перехода), то есть должны ловиться именно здесь.
 *
 * <p>Дополнительно сам факт поднятия контекста означает, что
 * {@link TransitionMapStartupValidator} не нашёл нарушений — он падает при старте.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransitionMapValidatorIntegrationTest {

    @Autowired
    private StateMachineConfigRepository stateMachineConfigRepository;

    @Autowired
    private StateConfigRepository stateConfigRepository;

    @Autowired
    private TransitionConfigRepository transitionConfigRepository;

    @Autowired
    private TransitionMapValidator validator;

    @Test
    void everySeededTransitionMapIsValid() {
        List<StateMachineConfig> published = publishedConfigs();
        assertThat(published).as("в сидах должны быть опубликованные карты").isNotEmpty();

        List<String> failures = new ArrayList<>();
        for (StateMachineConfig config : published) {
            List<TransitionMapViolation> violations = validator.validate(config);
            if (!violations.isEmpty()) {
                failures.add("%s/%s v%d: %s".formatted(
                        config.getEntityType(), config.getProcessType(), config.getVersion(),
                        TransitionMapViolation.describe(violations)));
            }
        }

        assertThat(failures).as("карты переходов из миграций должны быть корректны").isEmpty();
    }

    @Test
    void everySeededMapEmitsOnlyEventsFromContract() {
        var nonCanonical = publishedConfigs().stream()
                .flatMap(config -> transitionConfigRepository.findByConfigId(config.getId()).stream())
                .flatMap(t -> t.getEmits().stream())
                .distinct()
                .filter(event -> !KnownEventTypes.isKnown(event))
                .toList();

        assertThat(nonCanonical)
                .as("все emits из сидов должны присутствовать в 06_event_contract.md §18")
                .isEmpty();
    }

    @Test
    void validateOrThrowAcceptsSeededMaps() {
        for (StateMachineConfig config : publishedConfigs()) {
            assertThatCode(() -> validator.validateOrThrow(config))
                    .as("карта %s", config.getId())
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void validatePublishedFindsNoViolationsInSeededMaps() {
        assertThat(validator.validatePublished()).isEmpty();
    }

    private List<StateMachineConfig> publishedConfigs() {
        return stateMachineConfigRepository.findAll().stream()
                .filter(config -> config.getStatus() == LifecycleStatus.PUBLISHED)
                .toList();
    }

    /**
     * Валидатор обнаруживает неоднозначность на РЕАЛЬНОЙ карте: берутся два активных перехода
     * из одной пары {@code (from_state, trigger)}, и одному из них подменяются guards на набор
     * другого. Правки только в памяти — в БД ничего не пишется.
     */
    @Test
    void detectsAmbiguityIntroducedIntoRealMap() {
        for (StateMachineConfig config : publishedConfigs()) {
            List<TransitionConfig> transitions = new ArrayList<>(
                    transitionConfigRepository.findByConfigId(config.getId()));

            for (int i = 0; i < transitions.size(); i++) {
                for (int j = i + 1; j < transitions.size(); j++) {
                    TransitionConfig first = transitions.get(i);
                    TransitionConfig second = transitions.get(j);
                    if (!first.isActive() || !second.isActive()
                            || !first.getFromState().equals(second.getFromState())
                            || first.getTrigger() != second.getTrigger()
                            || first.getToState().equals(second.getToState())) {
                        continue;
                    }

                    // Приводим наборы guards к одинаковым — теперь исход различается только
                    // порядком кодов, и валидатор обязан это увидеть.
                    second.setGuards(new ArrayList<>(first.getGuards()));

                    List<TransitionMapViolation> violations = validator.validate(
                            config,
                            stateConfigRepository.findByConfigId(config.getId()),
                            transitions);

                    assertThat(violations.stream().map(TransitionMapViolation::code))
                            .as("карта %s, переходы %s / %s", config.getId(),
                                    first.getCode(), second.getCode())
                            .contains(TransitionMapErrorCode.AMBIGUOUS_TRANSITION);
                    return;
                }
            }
        }

        throw new AssertionError("не нашлось карты с несколькими активными переходами из одного "
                + "(from_state, trigger) — тест не смог создать неоднозначность");
    }

    /** Startup-валидатор на реальных (корректных) картах не должен падать. */
    @Test
    void startupValidatorAcceptsSeededMaps() {
        assertThatCode(() -> new TransitionMapStartupValidator(stateMachineConfigRepository, validator)
                .run(null))
                .doesNotThrowAnyException();
    }

    /** Валидатор отдаёт полный список нарушений, а не первое. */
    @Test
    void validateOrThrowReportsAllViolationsForBrokenMap() {
        StateMachineConfig config = publishedConfigs().stream()
                .filter(c -> !transitionConfigRepository.findByConfigId(c.getId()).isEmpty())
                .findFirst()
                .orElseThrow();

        List<TransitionConfig> broken = new ArrayList<>(
                transitionConfigRepository.findByConfigId(config.getId()));
        TransitionConfig first = broken.get(0);
        String originalToState = first.getToState();
        String originalGuards = String.join(",", first.getGuards());
        String originalEmits = String.join(",", first.getEmits());

        try {
            first.setToState("NonexistentState");
            first.setGuards(new ArrayList<>(List.of("NoSuchGuard")));
            first.setEmits(new ArrayList<>(List.of("not.an.event")));

            List<TransitionMapViolation> violations = validator.validate(
                    config, stateConfigRepository.findByConfigId(config.getId()), broken);

            assertThat(violations.stream().map(TransitionMapViolation::code))
                    .contains(
                            TransitionMapErrorCode.STATE_UNKNOWN,
                            TransitionMapErrorCode.GUARD_NOT_FOUND,
                            TransitionMapErrorCode.EVENT_UNKNOWN);

            // Исключение собирает все нарушения в одно сообщение — по одному на строку.
            InvalidTransitionMapException thrown = new InvalidTransitionMapException("test", violations);
            assertThat(thrown.violations()).hasSize(violations.size());
            assertThat(thrown).hasMessageContaining("GUARD_NOT_FOUND")
                    .hasMessageContaining("EVENT_UNKNOWN")
                    .hasMessageContaining("STATE_UNKNOWN");
        } finally {
            // Сущность управляемая и живёт в общей БД Testcontainers — обязательно вернуть
            // исходные значения, иначе испортим карту для остальных тестов прогона.
            first.setToState(originalToState);
            first.setGuards(new ArrayList<>(originalGuards.isEmpty()
                    ? List.of() : List.of(originalGuards.split(","))));
            first.setEmits(new ArrayList<>(originalEmits.isEmpty()
                    ? List.of() : List.of(originalEmits.split(","))));
        }
    }
}
