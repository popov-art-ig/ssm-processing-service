package ru.coordination.approval.service.guard;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageRepository;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-P-008 «Все этапы согласованы или согласованы с замечаниями, хотя бы один — с замечаниями» —
 * вторая половина условия (PHASE-07, источник: {@code 05_guards_actions_registry.md} §4.1).
 * Резолвится {@code guard_registry.handler = 'hasCommentsGuard'} (миграция V22, код реестра
 * {@code HasComments}); в карте переходов указан как {@code HasComments} в guards перехода
 * {@code ProcessApprovedWithComments} ({@code InProgress → ApprovedWithComments}).
 *
 * <p><b>Контракт.</b> {@code true} — хотя бы один этап процесса в статусе
 * {@code ApprovedWithComments}; {@code false} — таких этапов нет. Побочных эффектов нет.
 *
 * <p><b>Реализована только часть G-P-008.</b> Спецификация требует двух условий:
 * «∀ stage: {@code Approved} или {@code ApprovedWithComments}» <i>и</i>
 * «∃ stage: {@code ApprovedWithComments}». Здесь проверяется только второе: полноту завершения
 * гарантирует парный guard {@link AllStagesCompletedGuard} в том же списке {@code guards} этого
 * перехода (оба обязаны пройти), поэтому отдельная проверка «все согласованы» была бы
 * дублированием и лишним запросом к БД. {@code false} при этом означает «итог процесса —
 * без замечаний», а не «процесс не завершён».
 *
 * <p><b>Расхождение в коде реестра.</b> Миграция V22 приписывает условию код {@code G-P-007}, но
 * в спецификации {@code G-P-007} — это {@code AllStagesApproved} (§4.1), а
 * {@code AllStagesApprovedWithComments} — {@code G-P-008}; второго условия
 * {@code G-P-008} («хотя бы один с замечаниями») спецификация в отдельный guard не выделяет.
 *
 * <p>Логика формирования итога процесса (источник: {@code 02_process_types.md} §3.4):
 * если хотя бы один этап завершён как {@code ApprovedWithComments} → процесс переходит в
 * {@code ApprovedWithComments}, иначе (все этапы {@code Approved}) → процесс переходит в
 * {@code Approved}.
 *
 * <p>Статусы читаются из {@link StageRepository}, а не из {@code process.getStages()}:
 * guard вызывается из {@code EvaluateProcessCompletionAction} внутри перехода закрытия этапа,
 * и в памяти закрываемый этап мог ещё числиться {@code Active} — тогда процесс завершался как
 * {@code Approved}, теряя признак «с замечаниями».
 */
@Component("hasCommentsGuard")
@RequiredArgsConstructor
public class HasCommentsGuard implements Guard {

    private final StageRepository stageRepository;

    /**
     * @return {@code true}, если хотя бы один этап процесса имеет статус
     *         {@code ApprovedWithComments}
     */
    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof ProcessInstance process)) {
            return false;
        }

        List<String> statuses = stageRepository.findStatusesByProcessId(process.getId());
        return statuses.stream().anyMatch("ApprovedWithComments"::equals);
    }
}
