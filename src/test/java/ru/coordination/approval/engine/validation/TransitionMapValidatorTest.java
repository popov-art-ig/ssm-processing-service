package ru.coordination.approval.engine.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.common.RegistryScope;
import ru.coordination.approval.domain.registry.ActionRegistryEntry;
import ru.coordination.approval.domain.registry.GuardRegistryEntry;
import ru.coordination.approval.domain.statemachine.StateConfig;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.domain.statemachine.TransitionConfig;
import ru.coordination.approval.domain.statemachine.TriggerType;
import ru.coordination.approval.engine.registry.ActionRegistryRepository;
import ru.coordination.approval.engine.registry.GuardRegistryRepository;

/**
 * Проверки валидатора карты переходов ({@code 05_guards_actions_registry.md} §7.1).
 *
 * <p>Каждый тест соответствует либо проверке из спецификации, либо классу дефектов, найденных
 * аудитом 2026-10-08 (см. {@link TransitionMapErrorCode}).
 */
@ExtendWith(MockitoExtension.class)
class TransitionMapValidatorTest {

    @Mock
    private GuardRegistryRepository guardRegistryRepository;

    @Mock
    private ActionRegistryRepository actionRegistryRepository;

    private TransitionMapValidator validator;
    private StateMachineConfig config;

    private final List<GuardRegistryEntry> guards = new ArrayList<>();
    private final List<ActionRegistryEntry> actions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        validator = new TransitionMapValidator(
                null, // StateMachineConfigRepository нужен только перегрузке validate(config)
                null,
                null,
                guardRegistryRepository,
                actionRegistryRepository);

        guards.clear();
        actions.clear();
        // lenient: часть тестов не обращается к реестрам (например, проверки состояний).
        lenient().when(guardRegistryRepository.findAll()).thenAnswer(invocation -> List.copyOf(guards));
        lenient().when(actionRegistryRepository.findAll()).thenAnswer(invocation -> List.copyOf(actions));

        config = StateMachineConfig.builder()
                .id(UUID.randomUUID())
                .entityType(EntityType.PROCESS)
                .processType(ProcessType.STANDARD)
                .version(1)
                .status(LifecycleStatus.PUBLISHED)
                .createdAt(Instant.now())
                .createdBy(UUID.randomUUID())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void returnsNoViolationsForCorrectMap() {
        registerGuard("IsInitiator", "PROCESS");
        registerAction("SetStartedAt", "PROCESS");

        List<TransitionMapViolation> violations = validator.validate(
                config,
                List.of(state("Draft", true), state("InProgress", false)),
                List.of(transition("StartProcess", "Draft", "InProgress", TriggerType.USER_ACTION,
                        List.of("IsInitiator"), List.of("SetStartedAt"),
                        List.of("approval.process.started"), true)));

        assertThat(violations).isEmpty();
    }

    @Test
    void reportsGuardNotFound() {
        registerAction("SetStartedAt", "PROCESS");

        List<TransitionMapViolation> violations = validateSingle(
                transition("StartProcess", "Draft", "InProgress", TriggerType.USER_ACTION,
                        List.of("NoSuchGuard"), List.of("SetStartedAt"),
                        List.of("approval.process.started"), true));

        assertThat(codes(violations)).containsExactly(TransitionMapErrorCode.GUARD_NOT_FOUND);
    }

    @Test
    void reportsActionNotFound() {
        registerGuard("IsInitiator", "PROCESS");

        List<TransitionMapViolation> violations = validateSingle(
                transition("StartProcess", "Draft", "InProgress", TriggerType.USER_ACTION,
                        List.of("IsInitiator"), List.of("NoSuchAction"),
                        List.of("approval.process.started"), true));

        assertThat(codes(violations)).containsExactly(TransitionMapErrorCode.ACTION_NOT_FOUND);
    }

    @Test
    void reportsUnknownEvent() {
        registerGuard("IsInitiator", "PROCESS");
        registerAction("SetStartedAt", "PROCESS");

        List<TransitionMapViolation> violations = validateSingle(
                transition("StartProcess", "Draft", "InProgress", TriggerType.USER_ACTION,
                        List.of("IsInitiator"), List.of("SetStartedAt"),
                        List.of("template.published"), true));

        assertThat(codes(violations)).containsExactly(TransitionMapErrorCode.EVENT_UNKNOWN);
        assertThat(violations.get(0).message()).contains("template.published");
    }

    @Test
    void reportsUnknownFromState() {
        registerGuard("IsInitiator", "PROCESS");
        registerAction("SetStartedAt", "PROCESS");

        List<TransitionMapViolation> violations = validator.validate(
                config,
                List.of(state("Draft", true)),
                List.of(transition("StartProcess", "GhostState", "Draft", TriggerType.USER_ACTION,
                        List.of("IsInitiator"), List.of("SetStartedAt"), List.of(), true)));

        assertThat(codes(violations)).contains(TransitionMapErrorCode.STATE_UNKNOWN);
    }

    @Test
    void reportsUnknownToState() {
        List<TransitionMapViolation> violations = validator.validate(
                config,
                List.of(state("Draft", true)),
                List.of(transition("StartProcess", "Draft", "GhostState", TriggerType.USER_ACTION,
                        List.of(), List.of(), List.of(), true)));

        assertThat(codes(violations)).contains(TransitionMapErrorCode.STATE_UNKNOWN);
    }

    @Test
    void reportsMissingInitialState() {
        List<TransitionMapViolation> violations = validator.validate(
                config,
                List.of(state("Draft", false), state("InProgress", false)),
                List.of(transition("StartProcess", "Draft", "InProgress", TriggerType.USER_ACTION,
                        List.of(), List.of(), List.of(), true)));

        assertThat(codes(violations)).contains(TransitionMapErrorCode.INITIAL_STATE_MISSING);
    }

    @Test
    void reportsMultipleInitialStates() {
        List<TransitionMapViolation> violations = validator.validate(
                config,
                List.of(state("Draft", true), state("InProgress", true)),
                List.of(transition("StartProcess", "Draft", "InProgress", TriggerType.USER_ACTION,
                        List.of(), List.of(), List.of(), true)));

        assertThat(codes(violations)).contains(TransitionMapErrorCode.MULTIPLE_INITIAL_STATES);
    }

    @Test
    void reportsDuplicateTransitionCodeForSameFromState() {
        List<TransitionMapViolation> violations = validator.validate(
                config,
                List.of(state("Draft", true), state("InProgress", false), state("Recalled", false)),
                List.of(
                        transition("StartProcess", "Draft", "InProgress", TriggerType.USER_ACTION,
                                List.of(), List.of(), List.of(), true),
                        transition("StartProcess", "Draft", "Recalled", TriggerType.USER_ACTION,
                                List.of(), List.of(), List.of(), false)));

        assertThat(codes(violations)).contains(TransitionMapErrorCode.DUPLICATE_TRANSITION_CODE);
    }

    /**
     * Ключевая проверка аудита: два активных перехода из одной пары {@code (from_state, trigger)}
     * в разные состояния и с ОДИНАКОВЫМ набором guards. Именно такой набор давал
     * «rejectRemark → Processed» и «возобновление → отзыв»: исход зависел от лексикографического
     * порядка кодов, а не от условий.
     */
    @Test
    void reportsAmbiguousTransition() {
        List<TransitionMapViolation> violations = validator.validate(
                config,
                List.of(state("OnRework", true), state("InProgress", false), state("Recalled", false)),
                List.of(
                        transition("ResumeProcess", "OnRework", "InProgress", TriggerType.USER_ACTION,
                                List.of("IsInitiator"), List.of(), List.of(), true),
                        transition("RecallProcess", "OnRework", "Recalled", TriggerType.USER_ACTION,
                                List.of("IsInitiator"), List.of(), List.of(), true)));

        assertThat(codes(violations)).contains(TransitionMapErrorCode.AMBIGUOUS_TRANSITION);
    }

    /**
     * Разные guards из одной пары {@code (from_state, trigger)} — осознанное конфигурирование, а
     * не дефект: «с замечаниями» и обычное «одобрено» различаются именно guards, priority задаёт
     * порядок проверки. Такие карты есть в сидах, и они не должны считаться некорректными.
     */
    @Test
    void doesNotReportAmbiguityWhenGuardsDiffer() {
        registerGuard("AllStagesCompleted", "PROCESS");
        registerGuard("HasComments", "PROCESS");

        List<TransitionMapViolation> violations = validator.validate(
                config,
                List.of(state("InProgress", true), state("Approved", false), state("ApprovedWithComments", false)),
                List.of(
                        transition("ProcessApprovedWithComments", "InProgress", "ApprovedWithComments",
                                TriggerType.SYSTEM_ACTION,
                                List.of("AllStagesCompleted", "HasComments"), List.of(), List.of(), true),
                        transition("ProcessApproved", "InProgress", "Approved", TriggerType.SYSTEM_ACTION,
                                List.of("AllStagesCompleted"), List.of(), List.of(), true)));

        assertThat(codes(violations)).doesNotContain(TransitionMapErrorCode.AMBIGUOUS_TRANSITION);
    }

    @Test
    void doesNotReportAmbiguityWhenTargetsAreTheSame() {
        List<TransitionMapViolation> violations = validator.validate(
                config,
                List.of(state("OnRework", true), state("InProgress", false)),
                List.of(
                        transition("ResumeProcess", "OnRework", "InProgress", TriggerType.USER_ACTION,
                                List.of(), List.of(), List.of(), true),
                        transition("ResumeProcessAlt", "OnRework", "InProgress", TriggerType.USER_ACTION,
                                List.of(), List.of(), List.of(), true)));

        assertThat(codes(violations)).doesNotContain(TransitionMapErrorCode.AMBIGUOUS_TRANSITION);
    }

    @Test
    void doesNotReportAmbiguityForInactiveTransitions() {
        List<TransitionMapViolation> violations = validator.validate(
                config,
                List.of(state("OnRework", true), state("InProgress", false), state("Recalled", false)),
                List.of(
                        transition("ResumeProcess", "OnRework", "InProgress", TriggerType.USER_ACTION,
                                List.of(), List.of(), List.of(), true),
                        transition("RecallProcess", "OnRework", "Recalled", TriggerType.USER_ACTION,
                                List.of(), List.of(), List.of(), false)));

        assertThat(codes(violations)).doesNotContain(TransitionMapErrorCode.AMBIGUOUS_TRANSITION);
    }

    /** Guard процессного уровня в карте этапа — именно это давало ClassCastException. */
    @Test
    void reportsApplicabilityMismatchForGuard() {
        registerGuard("AllMandatorySlotsFilled", "PROCESS");
        config.setEntityType(EntityType.STAGE);

        List<TransitionMapViolation> violations = validator.validate(
                config,
                List.of(state("Pending", true), state("Active", false)),
                List.of(transition("ActivateStage", "Pending", "Active", TriggerType.SYSTEM_ACTION,
                        List.of("AllMandatorySlotsFilled"), List.of(), List.of(), true)));

        assertThat(codes(violations)).contains(TransitionMapErrorCode.APPLICABILITY_MISMATCH);
    }

    @Test
    void reportsApplicabilityMismatchForAction() {
        registerAction("AssignStageTasks", "PROCESS");
        config.setEntityType(EntityType.STAGE);

        List<TransitionMapViolation> violations = validator.validate(
                config,
                List.of(state("Pending", true), state("Active", false)),
                List.of(transition("ActivateStage", "Pending", "Active", TriggerType.SYSTEM_ACTION,
                        List.of(), List.of("AssignStageTasks"), List.of(), true)));

        assertThat(codes(violations)).contains(TransitionMapErrorCode.APPLICABILITY_MISMATCH);
    }

    @Test
    void treatsEmptyApplicableEntitiesAsApplicableEverywhere() {
        registerGuard("LegacyGuard", null);
        registerAction("LegacyAction", null);

        List<TransitionMapViolation> violations = validator.validate(
                config,
                List.of(state("Draft", true), state("InProgress", false)),
                List.of(transition("StartProcess", "Draft", "InProgress", TriggerType.USER_ACTION,
                        List.of("LegacyGuard"), List.of("LegacyAction"), List.of(), true)));

        assertThat(violations).isEmpty();
    }

    @Test
    void reportsInactiveGuardAndActionAsNotFound() {
        registerGuard("IsInitiator", "PROCESS", false);
        registerAction("SetStartedAt", "PROCESS", false);

        List<TransitionMapViolation> violations = validateSingle(
                transition("StartProcess", "Draft", "InProgress", TriggerType.USER_ACTION,
                        List.of("IsInitiator"), List.of("SetStartedAt"), List.of(), true));

        assertThat(codes(violations))
                .contains(TransitionMapErrorCode.GUARD_NOT_FOUND, TransitionMapErrorCode.ACTION_NOT_FOUND);
    }

    @Test
    void reportAllViolationsAtOnceInsteadOfFirstOne() {
        List<TransitionMapViolation> violations = validateSingle(
                transition("StartProcess", "Draft", "InProgress", TriggerType.USER_ACTION,
                        List.of("NoSuchGuard"), List.of("NoSuchAction"),
                        List.of("unknown.event"), true));

        assertThat(codes(violations)).containsExactlyInAnyOrder(
                TransitionMapErrorCode.GUARD_NOT_FOUND,
                TransitionMapErrorCode.ACTION_NOT_FOUND,
                TransitionMapErrorCode.EVENT_UNKNOWN);
    }

    // ------------------------------------------------------------------ вспомогательное

    private List<TransitionMapViolation> validateSingle(TransitionConfig transition) {
        return validator.validate(
                config,
                List.of(state("Draft", true), state("InProgress", false)),
                List.of(transition));
    }

    private static List<TransitionMapErrorCode> codes(List<TransitionMapViolation> violations) {
        return violations.stream().map(TransitionMapViolation::code).toList();
    }

    private static StateConfig state(String code, boolean initial) {
        return StateConfig.builder()
                .id(UUID.randomUUID())
                .code(code)
                .displayName(code)
                .initial(initial)
                .createdAt(Instant.now())
                .build();
    }

    private static TransitionConfig transition(
            String code,
            String fromState,
            String toState,
            TriggerType trigger,
            List<String> guards,
            List<String> actions,
            List<String> emits,
            boolean active) {

        return TransitionConfig.builder()
                .id(UUID.randomUUID())
                .code(code)
                .fromState(fromState)
                .toState(toState)
                .trigger(trigger)
                .guards(new ArrayList<>(guards))
                .actions(new ArrayList<>(actions))
                .emits(new ArrayList<>(emits))
                .priority(0)
                .active(active)
                .createdAt(Instant.now())
                .build();
    }

    private void registerGuard(String code, String applicableEntity) {
        registerGuard(code, applicableEntity, true);
    }

    private void registerGuard(String code, String applicableEntity, boolean active) {
        guards.add(GuardRegistryEntry.builder()
                .code(code)
                .displayName(code)
                .description(code)
                .handler(code)
                .scope(RegistryScope.GLOBAL)
                .applicableEntities(applicableEntity == null ? List.of() : List.of(applicableEntity))
                .active(active)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private void registerAction(String code, String applicableEntity) {
        registerAction(code, applicableEntity, true);
    }

    private void registerAction(String code, String applicableEntity, boolean active) {
        actions.add(ActionRegistryEntry.builder()
                .code(code)
                .displayName(code)
                .description(code)
                .handler(code)
                .scope(RegistryScope.GLOBAL)
                .applicableEntities(applicableEntity == null ? List.of() : List.of(applicableEntity))
                .active(active)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }
}
