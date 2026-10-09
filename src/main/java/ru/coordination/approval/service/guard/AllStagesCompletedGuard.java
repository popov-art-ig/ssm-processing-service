package ru.coordination.approval.service.guard;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageRepository;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-P-006 «Все этапы процесса завершены» (PHASE-07, источник: {@code 05_guards_actions_registry.md}
 * §4.1). Резолвится {@code guard_registry.handler = 'allStagesCompletedGuard'} (миграция V22).
 *
 * <p>Проверяет, что все этапы процесса в одном из финальных статусов: {@code Approved},
 * {@code ApprovedWithComments} или {@code Completed} (последний для UNIFIED процессов, PHASE-13).
 *
 * <p>Статусы читаются из {@link StageRepository}, а не из {@code process.getStages()}: коллекция
 * в памяти может содержать устаревшие значения, и до перевода на репозиторий процесс не
 * завершался вовсе.
 *
 * <p>Проверка «все этапы» не включает никаких исключений: этап, ради закрытия которого
 * запускается проверка, уже записан в БД как финальный (<code>TransitionEngine</code> пишет
 * статус до выполнения actions), поэтому если какой-то другой этап ещё не завершён, условие
 * ложно и процесс не закрывается. Дополнительно {@code EvaluateProcessCompletionAction}
 * запускает проверку только для последнего этапа маршрута — промежуточный этап закрывается
 * без оценки завершённости процесса.
 */
@Component("allStagesCompletedGuard")
@RequiredArgsConstructor
public class AllStagesCompletedGuard implements Guard {

    private final StageRepository stageRepository;

    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof ProcessInstance process)) {
            return false;
        }

        List<String> statuses = stageRepository.findStatusesByProcessId(process.getId());
        if (statuses.isEmpty()) {
            return false;
        }

        return statuses.stream().allMatch(status ->
                "Approved".equals(status)
                        || "ApprovedWithComments".equals(status)
                        || "Completed".equals(status));
    }
}
