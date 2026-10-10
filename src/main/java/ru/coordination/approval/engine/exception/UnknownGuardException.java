package ru.coordination.approval.engine.exception;

/**
 * Код guard из {@code transition_config.guards[]} отсутствует в {@code guard_registry} (или
 * запись неактивна) — рассинхронизация конфига переходов и реестра guards.
 */
public class UnknownGuardException extends RuntimeException {

    /** @param code код guard'а из {@code transition_config.guards[]}, для которого нет активной записи реестра */
    public UnknownGuardException(String code) {
        super("No active guard_registry entry for code: " + code);
    }
}
