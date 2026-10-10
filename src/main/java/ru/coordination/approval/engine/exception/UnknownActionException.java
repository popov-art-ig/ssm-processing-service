package ru.coordination.approval.engine.exception;

/**
 * Код action из {@code transition_config.actions[]} отсутствует в {@code action_registry}
 * (или запись неактивна) — рассинхронизация конфига переходов и реестра actions.
 */
public class UnknownActionException extends RuntimeException {

    /** @param code код action'а из {@code transition_config.actions[]}, для которого нет активной записи реестра */
    public UnknownActionException(String code) {
        super("No active action_registry entry for code: " + code);
    }
}
