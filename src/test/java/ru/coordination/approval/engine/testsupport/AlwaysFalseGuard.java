package ru.coordination.approval.engine.testsupport;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * Тестовая guard-заглушка (PHASE-02, T6) — всегда блокирует переход. См.
 * {@link AlwaysTrueGuard} за объяснением, почему заглушка безопасна для прод-конфигурации.
 */
@Component("testAlwaysFalseGuard")
@Profile("test")
public class AlwaysFalseGuard implements Guard {

    @Override
    public boolean evaluate(TransitionContext context) {
        return false;
    }
}
