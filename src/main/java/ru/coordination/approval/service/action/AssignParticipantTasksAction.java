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
 * A-S-001 «Назначить задачи участникам» (PHASE-04, источник:
 * {@code 05_guards_actions_registry.md} §5.2). Резолвится {@code action_registry.handler =
 * 'assignParticipantTasksAction'} (миграция V19); включён в actions переходов {@code ActivateStage}
 * ({@code Pending → Active}) и {@code ReactivateStage} ({@code → Active} из трёх исходных
 * состояний — {@code OnRework}, {@code Approved}, {@code ApprovedWithComments}, V23).
 *
 * <p><b>Побочный эффект</b> — статусы участников текущей итерации: при
 * {@code executionOrder = SEQUENTIAL} первый по {@code orderIdx} переводится в {@code Assigned}
 * с {@code assignedAt}, остальные — в {@code Pending}; при {@code PARALLEL} или незаданном
 * (значение по умолчанию) в {@code Assigned} с {@code assignedAt} переводятся все. Сами записи
 * {@link Participant} не создаются (см. упрощение ниже), поэтому публикация
 * {@code approval.task.assigned} из §5.2 не выполняется — события появятся вместе с
 * Event Publisher (PHASE-19).
 *
 * <p><b>Расхождение в коде реестра.</b> Миграция V19 регистрирует действие под кодом
 * {@code A-S-002}, которым в спецификации обозначено {@code SetStageStartedAt}; описание реестра
 * само отмечает это как «A-S-002… A-S-001 по нумерации спеки». Здесь используется код
 * спецификации — {@code A-S-001}.
 *
 * <p><b>Упрощение этой фазы</b> (тикет, раздел 4): работает с участниками текущей (последней
 * по {@code iterationIdx}) итерации этапа, которая предполагается уже существующей на момент
 * активации. Создание новой итерации при активации не реализуется — требует
 * {@code RouteGeneratorService}, которого нет (см. также аналогичное упрощение
 * {@code AllMandatorySlotsFilledGuard}, PHASE-03).
 *
 * <p><b>Почему ветка {@code SEQUENTIAL} явно сбрасывает остальных в {@code Pending}.</b> При
 * повторной активации этапа после возврата на доработку участники клонируются уже со статусом
 * {@code Pending} ({@link ActivateTargetStageAction}), но на {@code PARALLEL}-этапе, где очередь
 * не ведётся, их статусы могли остаться {@code Assigned}/{@code Decided} от предыдущей итерации.
 * Безусловный перевод всех, кроме первого, в {@code Pending} делает результат действия идемпотентным
 * и не зависящим от предыдущего состояния клонированных записей.
 *
 * <p>Отсутствие итераций у этапа вообще — ошибка данных (этап без итераций не может быть
 * активирован осмысленно), бросается {@link IllegalStateException}. Пустая (но существующая)
 * текущая итерация без участников — не ошибка, действие просто ничего не делает.
 */
@Component("assignParticipantTasksAction")
public class AssignParticipantTasksAction implements Action {

    /**
     * Переводит участников текущей итерации в {@code Assigned} (все — при {@code PARALLEL},
     * только первый по {@code orderIdx} — при {@code SEQUENTIAL}, остальные в {@code Pending}).
     *
     * @throws IllegalStateException если у этапа нет ни одной итерации
     */
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
