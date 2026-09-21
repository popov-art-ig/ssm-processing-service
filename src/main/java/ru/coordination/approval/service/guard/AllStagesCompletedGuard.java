package ru.coordination.approval.service.guard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-P-006 «Все этапы процесса завершены» (PHASE-07, источник: {@code 05_guards_actions_registry.md}
 * §4.1). Резолвится {@code guard_registry.handler = 'allStagesCompletedGuard'} (миграция V22).
 *
 * <p>Проверяет, что все этапы процесса находятся в одном из финальных статусов: {@code Approved},
 * {@code ApprovedWithComments} или {@code Completed} (последний для UNIFIED процессов, PHASE-13).
 *
 * <p>Используется в переходах {@code ProcessApproved} и {@code ProcessApprovedWithComments} для
 * автоматического завершения процесса после закрытия последнего этапа.
 */
@Component("allStagesCompletedGuard")
@RequiredArgsConstructor
public class AllStagesCompletedGuard implements Guard {

    @Override
    public boolean evaluate(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();

        if (process.getStages().isEmpty()) {
            return false;
        }

        return process.getStages().stream().allMatch(stage -> {
            String status = stage.getStatus();
            return "Approved".equals(status)
                    || "ApprovedWithComments".equals(status)
                    || "Completed".equals(status);
        });
    }
}
