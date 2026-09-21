package ru.coordination.approval.service.action;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

/**
 * A-P-002 «Завершить процесс» (PHASE-07, источник: {@code 05_guards_actions_registry.md} §5.1).
 * Резолвится {@code action_registry.handler = 'completeProcessAction'} (миграция V22).
 *
 * <p>Устанавливает временную метку {@code completedAt} на процессе. Вызывается как action переходов
 * {@code ProcessApproved} и {@code ProcessApprovedWithComments} (уровень {@code PROCESS}).
 *
 * <p>Сохранение изменений происходит автоматически — {@link ru.coordination.approval.engine.TransitionEngine}
 * работает в {@code @Transactional} контексте, и все изменения managed-сущностей фиксируются при
 * коммите транзакции.
 */
@Component("completeProcessAction")
@RequiredArgsConstructor
public class CompleteProcessAction implements Action {

    @Override
    public void execute(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();
        process.setCompletedAt(Instant.now());
        // Сохранение произойдёт автоматически (TransitionEngine работает в @Transactional)
    }
}
