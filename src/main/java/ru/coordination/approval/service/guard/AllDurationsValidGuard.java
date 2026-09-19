package ru.coordination.approval.service.guard;

import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-P-005 «У всех этапов срок > 0» (PHASE-03, источник: {@code 05_guards_actions_registry.md}
 * §4.1). Резолвится {@code guard_registry.handler = 'allDurationsValidGuard'} (миграция V18).
 *
 * <p>В проде уже гарантировано CHECK-констрейнтом {@code stage_instance.duration > 0}
 * (миграция V4), поэтому на реальных данных из БД этот guard всегда {@code true} — реализован
 * тем не менее буквально: как defensive-проверка и для юнит-теста с намеренно некорректными
 * (собранными в памяти, не сохранёнными) данными.
 */
@Component("allDurationsValidGuard")
public class AllDurationsValidGuard implements Guard {

    @Override
    public boolean evaluate(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();
        return process.getStages().stream()
                .allMatch(stage -> stage.getDuration() != null && stage.getDuration() > 0);
    }
}
