package ru.coordination.approval.service.guard;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageRepository;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-P-010 «Все этапы завершены» (PHASE-07, источник: {@code 05_guards_actions_registry.md} §4.1).
 * Резолвится {@code guard_registry.handler = 'allStagesCompletedGuard'} (миграция V22, код
 * реестра {@code AllStagesCompleted}); в карте переходов указан как {@code AllStagesCompleted} в
 * guards переходов {@code ProcessApproved} и {@code ProcessApprovedWithComments}
 * ({@code InProgress → Approved}/{@code ApprovedWithComments}, {@code SYSTEM_ACTION}).
 *
 * <p><b>Контракт.</b> {@code true} — все этапы процесса в финальном статусе, процесс можно
 * завершить; {@code false} — хотя бы один этап ещё не завершён, либо у процесса нет ни одного
 * этапа. Побочных эффектов нет; статус процесса пишет {@code TransitionEngine}.
 *
 * <p><b>Расхождение в коде реестра.</b> Миграция V22 приписывает условию код {@code G-P-006}, но
 * в спецификации {@code G-P-006} — это {@code AllActiveRemarksHandled} (§4.1),
 * {@code AllStagesCompleted} — {@code G-P-010} (§4.1). Ссылка в {@code guard_registry.description}
 * неточна; здесь указан код спецификации.
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

    /**
     * @return {@code true}, если каждый этап процесса имеет статус {@code Approved},
     *         {@code ApprovedWithComments} или {@code Completed}
     */
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
