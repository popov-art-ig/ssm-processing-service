package ru.coordination.approval.engine.validation;

import java.util.List;

/**
 * Нарушение карты переходов: код, место и человекочитаемое описание.
 *
 * <p>Собирается {@link TransitionMapValidator} целиком, а не бросается на первом нарушении —
 * при публикации карты нужно видеть сразу все проблемы.
 *
 * @param code    код нарушения ({@code 05_guards_actions_registry.md} §7.2)
 * @param where   где именно: код перехода, состояние или карта целиком
 * @param message описание проблемы и того, чем она опасна
 */
public record TransitionMapViolation(
        TransitionMapErrorCode code,
        String where,
        String message) {

    public static TransitionMapViolation of(TransitionMapErrorCode code, String where, String message) {
        return new TransitionMapViolation(code, where, message);
    }

    @Override
    public String toString() {
        return "%s at %s: %s".formatted(code, where, message);
    }

    /** Краткая сводка списка нарушений для сообщения исключения. */
    public static String describe(List<TransitionMapViolation> violations) {
        StringBuilder sb = new StringBuilder("Transition map is invalid: ")
                .append(violations.size())
                .append(" violation(s)");
        for (TransitionMapViolation violation : violations) {
            sb.append(System.lineSeparator()).append("  - ").append(violation);
        }
        return sb.toString();
    }
}
