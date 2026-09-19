package ru.coordination.approval.service.guard;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-S-001 «Предыдущий этап имеет финальный статус» (PHASE-04, источник:
 * {@code 05_guards_actions_registry.md} §4.2). Резолвится {@code guard_registry.handler =
 * 'previousStageCompletedGuard'} (миграция V19).
 *
 * <p>Спецификация не описывает случай «предыдущего этапа не существует» (первый этап
 * маршрута, минимальный {@code orderIdx}) — тикет явно решает: guard проходит ({@code true}),
 * блокировать нечего. Без этого допущения {@code StartProcess} не смог бы активировать ни
 * один процесс.
 */
@Component("previousStageCompletedGuard")
public class PreviousStageCompletedGuard implements Guard {

    private static final Set<String> COMPLETED_STATUSES = Set.of("Approved", "ApprovedWithComments", "Completed");

    @Override
    public boolean evaluate(TransitionContext context) {
        StageInstance stage = (StageInstance) context.entity();
        List<StageInstance> siblings = stage.getProcess().getStages();

        return siblings.stream()
                .filter(s -> s.getOrderIdx().equals(stage.getOrderIdx() - 1))
                .findFirst()
                .map(previous -> COMPLETED_STATUSES.contains(previous.getStatus()))
                .orElse(true);
    }
}
