package ru.coordination.approval.engine.validation;

import java.util.List;

/**
 * Карта переходов не прошла валидацию ({@code 05_guards_actions_registry.md} §7).
 *
 * <p>Бросается при публикации карты и на старте приложения — до того, как дефектная карта
 * начнёт исполняться. Содержит полный список нарушений, а не только первое.
 */
public class InvalidTransitionMapException extends RuntimeException {

    private final transient List<TransitionMapViolation> violations;

    public InvalidTransitionMapException(String mapDescription, List<TransitionMapViolation> violations) {
        super("Invalid transition map %s — %s".formatted(mapDescription, TransitionMapViolation.describe(violations)));
        this.violations = List.copyOf(violations);
    }

    public List<TransitionMapViolation> violations() {
        return violations;
    }
}
