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

    /**
     * @param mapDescription описание карты ({@code entityType/processType vN (id=...)}) — карт
     *                       одной пары {@code (entityType, processType)} одновременно несколько
     *                       версий (ADR-003), поэтому без id сообщение не говорит, какую править
     * @param violations     полный список нарушений; копируется, чтобы исключение не зависело от
     *                       изменяемого списка вызывающего кода
     */
    public InvalidTransitionMapException(String mapDescription, List<TransitionMapViolation> violations) {
        super("Invalid transition map %s — %s".formatted(mapDescription, TransitionMapViolation.describe(violations)));
        this.violations = List.copyOf(violations);
    }

    /**
     * Полный список нарушений: сохранён отдельно от текста сообщения, чтобы вызывающий код мог
     * разобрать отказ по кодам, а не по строке. Административный эндпоинт валидации карты
     * ({@code StateMachineConfigController.validateConfig}) использует не это исключение, а
     * результат {@code TransitionMapValidator.validate}, поэтому список читают тесты и код,
     * которому нужны коды нарушений.
     */
    public List<TransitionMapViolation> violations() {
        return violations;
    }
}
