package ru.coordination.approval.service.action;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.domain.statemachine.TriggerType;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.TransitionEngine;
import ru.coordination.approval.engine.TransitionResult;
import ru.coordination.approval.engine.model.StateMachineConfigRepository;
import ru.coordination.approval.engine.registry.Action;

/**
 * «Оценить завершение процесса» — проверяет, все ли этапы процесса завершены, и программно
 * запускает один из переходов {@code PROCESS} (PHASE-07). Резолвится
 * {@code action_registry.handler = 'evaluateProcessCompletionAction'} (миграция V22, код реестра
 * {@code EvaluateProcessCompletion}); включён в actions переходов {@code StageApproved} и
 * {@code StageApprovedWithComments} (уровень {@code STAGE}) после
 * {@link ActivateNextStageAction}. НЕ вызывается при переходе {@code StageOnRework} — возврат
 * процесса на доработку выполняет {@link EvaluateProcessReworkAction} (PHASE-08).
 *
 * <p><b>Кода в спецификации нет.</b> В {@code 05_guards_actions_registry.md} §5.2 приписанный
 * реестром V22 код {@code A-S-005} принадлежит действию {@code AutoApproveMissing}; действия
 * «оценить завершение процесса» спецификация не описывает — она предполагает, что завершение
 * процесса наступает по guard'ам {@code G-P-007}/{@code G-P-008}/{@code G-P-010} в переходах
 * процесса, а не по действию этапа.
 *
 * <p><b>Почему действие-аналог, а не прямой перевод процесса.</b> Процесс переводится вложенным
 * переходом того же движка ({@code PROCESS}/{@code SYSTEM_ACTION}) — состояние меняется только
 * через явный переход (ADR-002/ADR-028), а guards перехода
 * ({@code AllStagesCompleted}, {@code HasComments}) сами выбирают конкретный исход. Действие
 * лишь инициирует попытку, поэтому не дублирует логику выбора между {@code ProcessApproved} и
 * {@code ProcessApprovedWithComments} — это делает приоритет {@code priority = 10} в V22.
 *
 * <p>Вложенный вызов {@link TransitionEngine#transition} выполняется в той же транзакции, что и
 * внешний переход этапа. Если ни один переход процесса не выполнен
 * ({@link TransitionResult#performed()} {@code == false}, guards не прошли) — это не ошибка:
 * action вызывается после каждого закрытия этапа, но срабатывает только когда все этапы завершены
 * (guard {@code AllStagesCompleted}).
 */
@Component("evaluateProcessCompletionAction")
@RequiredArgsConstructor
public class EvaluateProcessCompletionAction implements Action {

    private final StateMachineConfigRepository stateMachineConfigRepository;
    private final TransitionEngine transitionEngine;

    /**
     * Пытается завершить процесс, если закрыт последний этап маршрута.
     *
     * @param context контекст перехода этапа; сущность — {@link StageInstance}, процесс берётся
     *                из {@code stage.getProcess()}
     * @throws IllegalStateException если нет {@code PROCESS}-конфига для типа процесса и версии
     *         процесса (ошибка конфигурации)
     */
    @Override
    public void execute(TransitionContext context) {
        StageInstance stage = (StageInstance) context.entity();
        ProcessInstance process = stage.getProcess();

        // Завершённость процесса оценивается ТОЛЬКО после закрытия последнего этапа маршрута.
        // Для промежуточного этапа проверка не имеет смысла: остальные этапы ещё не пройдены,
        // а сам закрываемый этап уже записан финальным (TransitionEngine пишет статус до
        // выполнения actions) — проверка «все этапы завершены» на этом шаге либо ложна, либо,
        // при единственном этапе, преждевременно истинна.
        boolean isLastStage = process.getStages().stream()
                .noneMatch(s -> s.getOrderIdx() > stage.getOrderIdx());
        if (!isLastStage) {
            return;
        }

        // Резолвить PROCESS config
        StateMachineConfig processConfig = stateMachineConfigRepository
                .findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.PROCESS,
                        process.getProcessType(),
                        process.getConfigVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "No StateMachineConfig for entityType=PROCESS, processType=%s, version=%d"
                                .formatted(process.getProcessType(), process.getConfigVersion())));

        // Создать контекст для перехода процесса (SYSTEM trigger)
        TransitionContext processContext =
                new TransitionContext(process, context.actorId(), ActorType.SYSTEM, Map.of());

        // Попытаться выполнить переход — TransitionEngine сам проверит guards и выберет
        // между ProcessApprovedWithComments (если есть комментарии) и ProcessApproved
        TransitionResult result = transitionEngine.transition(
                EntityType.PROCESS,
                process.getId(),
                processConfig.getId(),
                process.getStatus(),
                TriggerType.SYSTEM_ACTION,
                processContext,
                process::setStatus);

        // Если не выполнен (не все этапы завершены) — молча выйти
        if (!result.performed()) {
            // no-op: ещё не все этапы завершены или guards не прошли
        }
    }
}
