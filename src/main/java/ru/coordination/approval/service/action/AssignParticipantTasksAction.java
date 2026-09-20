package ru.coordination.approval.service.action;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.common.ExecutionOrder;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

/**
 * A-S-002 «Назначить задачи участникам» (PHASE-04, источник:
 * {@code 05_guards_actions_registry.md} §5.2). Резолвится {@code action_registry.handler =
 * 'assignParticipantTasksAction'} (миграция V19).
 *
 * <p><b>Упрощение этой фазы</b> (тикет, раздел 4): работает с участниками текущей (последней
 * по {@code iterationIdx}) итерации этапа, которая предполагается уже существующей на момент
 * активации. Создание новой итерации при активации не реализуется — требует
 * {@code RouteGeneratorService}, которого нет (см. также аналогичное упрощение
 * {@code AllMandatorySlotsFilledGuard}, PHASE-03).
 *
 * <p>Отсутствие итераций у этапа вообще — ошибка данных (этап без итераций не может быть
 * активирован осмысленно), бросается {@link IllegalStateException}. Пустая (но существующая)
 * текущая итерация без участников — не ошибка, действие просто ничего не делает.
 */
@Component("assignParticipantTasksAction")
public class AssignParticipantTasksAction implements Action {

    @Override
    public void execute(TransitionContext context) {
        StageInstance stage = (StageInstance) context.entity();
        StageIteration currentIteration = stage.getIterations().stream()
                .max(Comparator.comparing(StageIteration::getIterationIdx))
                .orElseThrow(() -> new IllegalStateException(
                        "Stage " + stage.getId() + " has no iterations, cannot assign participant tasks"));

        List<Participant> participants = currentIteration.getParticipants();
        Instant now = Instant.now();

        if (stage.getExecutionOrder() == ExecutionOrder.SEQUENTIAL) {
            Optional<Participant> first = participants.stream()
                    .min(Comparator.comparing(Participant::getOrderIdx));
            first.ifPresent(p -> {
                p.setStatus("Assigned");
                p.setAssignedAt(now);
            });
            participants.stream()
                    .filter(p -> first.isEmpty() || p != first.get())
                    .forEach(p -> p.setStatus("Pending"));
        } else {
            // PARALLEL или не задан (по умолчанию) — 05_guards_actions_registry.md §5.2.
            participants.forEach(p -> {
                p.setStatus("Assigned");
                p.setAssignedAt(now);
            });
        }
    }
}
