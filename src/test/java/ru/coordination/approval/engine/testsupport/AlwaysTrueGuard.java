package ru.coordination.approval.engine.testsupport;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * Тестовая guard-заглушка (PHASE-02, T6) — всегда пропускает переход. Живёт в
 * {@code src/test}, поэтому физически не попадает в production jar (сборка {@code bootJar}
 * пакует только {@code main}-sourceSet); {@code @Profile("test")} — вторая, независимая
 * гарантия на случай, если тестовый код когда-либо окажется на classpath вне тестового
 * запуска.
 */
@Component("testAlwaysTrueGuard")
@Profile("test")
public class AlwaysTrueGuard implements Guard {

    @Override
    public boolean evaluate(TransitionContext context) {
        return true;
    }
}
