package ru.coordination.approval.service.action;

import java.time.Instant;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

/**
 * A-P-002 «Установить время старта» — фиксирует {@code process.startedAt} (PHASE-03, источник:
 * {@code 05_guards_actions_registry.md} §5.1). Резолвится {@code action_registry.handler =
 * 'setStartedAtAction'} (миграция V18); включён в actions перехода {@code StartProcess}
 * ({@code Draft → InProgress}, {@code USER_ACTION}).
 *
 * <p><b>Побочный эффект</b> — единственный: {@code startedAt = Instant.now()} на
 * {@link ProcessInstance}. Явного {@code save()} нет: {@code TransitionEngine} работает в
 * {@code @Transactional}, и мутация managed-сущности фиксируется при коммите транзакции
 * (переход и actions выполняются в одной транзакции).
 *
 * <p>Порядок в {@code actions} перехода: {@code {AssignStageTasks,SetStartedAt}} (миграция V19).
 * {@code SetStartedAt} стоит последним, потому что активация первого этапа
 * ({@link AssignStageTasksAction}) выполняется вложенным переходом того же движка и не должна
 * видеть процесс уже стартовавшим.
 */
@Component("setStartedAtAction")
public class SetStartedAtAction implements Action {

    /** Записывает текущий момент в {@code process.startedAt}. */
    @Override
    public void execute(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();
        process.setStartedAt(Instant.now());
    }
}
