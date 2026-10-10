package ru.coordination.approval.service.action;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

/**
 * A-P-003 «Установить время завершения»: фиксирует {@code process.completedAt} (PHASE-07,
 * источник: {@code 05_guards_actions_registry.md} §5.1). Резолвится
 * {@code action_registry.handler = 'completeProcessAction'} (миграция V22, код реестра
 * {@code CompleteProcess}); включён в actions переходов {@code ProcessApproved} и
 * {@code ProcessApprovedWithComments} (уровень {@code PROCESS}).
 *
 * <p><b>Какое состояние меняет.</b> Только {@code completedAt = Instant.now()}; статус процесса
 * ({@code Approved} или {@code ApprovedWithComments}) записывает {@code TransitionEngine} по
 * {@code to_state} перехода. Выбор между двумя переходами делают guards
 * {@code AllStagesCompleted} и {@code HasComments} плюс приоритет {@code priority = 10} у
 * «с замечаниями» (V22).
 *
 * <p><b>Расхождение в коде реестра.</b> Миграция V22 приписывает действию код {@code A-P-002}, но
 * в спецификации {@code A-P-002} — это {@code SetStartedAt} (§5.1), а завершение процесса
 * описано как {@code A-P-003 SetCompletedAt}. Ссылка в {@code action_registry.description}
 * неточна; здесь указан код спецификации.
 *
 * <p><b>Не реализовано относительно §5.1.</b> Спецификация для {@code A-P-003} требует также
 * установить {@code autoArchiveScheduledAt = completedAt + 180 дней}; этого здесь нет —
 * автоархивация запланирована отдельным действием {@code A-P-007 ScheduleAutoArchive}, которое
 * в карту переходов не включено. Также не публикуется {@code approval.process.completed}:
 * события отданы {@code transition_config.emits} и Event Publisher (PHASE-19).
 *
 * <p>Сохранение изменений происходит автоматически — {@link ru.coordination.approval.engine.TransitionEngine}
 * работает в {@code @Transactional} контексте, и все изменения managed-сущностей фиксируются при
 * коммите транзакции.
 */
@Component("completeProcessAction")
@RequiredArgsConstructor
public class CompleteProcessAction implements Action {

    /** Записывает текущий момент в {@code process.completedAt}. */
    @Override
    public void execute(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();
        process.setCompletedAt(Instant.now());
        // Сохранение произойдёт автоматически (TransitionEngine работает в @Transactional)
    }
}
