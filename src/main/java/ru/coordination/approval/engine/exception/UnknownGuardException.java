package ru.coordination.approval.engine.exception;

/**
 * Код guard из {@code transition_config.guards[]} отсутствует в {@code guard_registry} (или
 * запись неактивна) — рассинхронизация конфига переходов и реестра guards.
 */
public class UnknownGuardException extends RuntimeException {

    public UnknownGuardException(String code) {
        super("No active guard_registry entry for code: " + code);
    }
}
