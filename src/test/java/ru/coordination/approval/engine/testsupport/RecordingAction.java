package ru.coordination.approval.engine.testsupport;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

/**
 * Тестовая action-заглушка (PHASE-02, T6) — не имеет реального побочного эффекта, только
 * запоминает вызовы для проверки в тестах (порядок и количество выполнений actions,
 * критерий приёмки 2). Singleton Spring-бин — {@link #reset()} нужно вызывать в начале
 * каждого теста, использующего этот бин. См. {@link AlwaysTrueGuard} за объяснением, почему
 * заглушка безопасна для прод-конфигурации.
 */
@Component("testRecordingAction")
@Profile("test")
public class RecordingAction implements Action {

    private final List<TransitionContext> invocations = new CopyOnWriteArrayList<>();

    @Override
    public void execute(TransitionContext context) {
        invocations.add(context);
    }

    public List<TransitionContext> invocations() {
        return Collections.unmodifiableList(invocations);
    }

    public void reset() {
        invocations.clear();
    }
}
