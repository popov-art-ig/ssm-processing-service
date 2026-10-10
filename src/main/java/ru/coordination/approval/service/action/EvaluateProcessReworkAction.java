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
 * Триггер перехода {@code ProcessRework} ({@code InProgress → OnRework}, {@code SYSTEM_ACTION})
 * (PHASE-08). Резолвится {@code action_registry.handler = 'evaluateProcessReworkAction'} (миграция
 * V23, код реестра {@code EvaluateProcessRework}); включён в actions перехода {@code StageOnRework}
 * ({@code Active → OnRework}, уровень {@code STAGE}) — то есть процесс «догоняет» этап, ушедший на
 * доработку.
 *
 * <p><b>Какое состояние меняет.</b> Статус {@link ProcessInstance} — программным вызовом
 * {@link TransitionEngine#transition} с {@code TriggerType.SYSTEM_ACTION} и
 * {@code ActorType.SYSTEM}. Вложенный переход выполняется в той же транзакции, что и внешний
 * переход этапа ({@code PROPAGATION.REQUIRED}), поэтому процесс не может оказаться в
 * {@code OnRework} без записанного статуса этапа.
 *
 * <p><b>Почему это action, а не прямое присваивание {@code process.status}.</b> Состояние меняется
 * только через явный переход state machine (ADR-002/ADR-028) — иначе не отработали бы guards
 * перехода ({@code HasStageOnRework}, V23) и не записался бы {@code AuditEvent}.
 *
 * <p><b>Почему «статус этапа уже виден guard'у».</b> {@code TransitionEngine} фиксирует новый
 * статус сущности <i>до</i> выполнения actions перехода, поэтому к моменту вызова этого действия
 * этап уже записан как {@code OnRework}, и
 * {@link ru.coordination.approval.service.guard.HasStageOnReworkGuard} видит его отдельным запросом
 * к {@code stage_instance}. По той же причине чтение статуса процесса идёт из {@code process},
 * который движок уже не менял (внешний переход относится к этапу).
 *
 * <p><b>Почему {@code performed == false} не считается ошибкой.</b> Это осознанный no-op:
 * повторный вызов (например, второй этап ушёл на доработку, когда процесс уже {@code OnRework})
 * не находит кандидата от текущего состояния, и попытка перевести процесс «ещё раз» не должна
 * ломать уже выполненный возврат. Отказ виден только в debug-логе {@code TransitionEngine}.
 *
 * <p><b>Кода в спецификации нет.</b> В {@code 05_guards_actions_registry.md} §5.1–5.3 нет действия
 * «оценить необходимость возврата процесса»: спецификация описывает возврат как операции над
 * <i>этапами</i> ({@code A-RT-001…A-RT-005}), а перевод процесса в {@code OnRework} выполняет
 * action перехода этапа.
 */
@Component("evaluateProcessReworkAction")
@RequiredArgsConstructor
public class EvaluateProcessReworkAction implements Action {

    private final StateMachineConfigRepository configRepository;
    private final TransitionEngine transitionEngine;

    /**
     * Пытается выполнить переход {@code ProcessRework} для процесса этапа.
     *
     * @param context контекст перехода {@code StageOnRework}; сущность — {@link StageInstance},
     *                процесс берётся из {@code stage.getProcess()}
     * @throws IllegalStateException если в БД нет {@code PROCESS}-конфига для типа процесса и
     *         версии, зафиксированной в процессе (ошибка конфигурации, а не бизнес-отказ)
     */
    @Override
    public void execute(TransitionContext context) {
        StageInstance stage = (StageInstance) context.entity();
        ProcessInstance process = stage.getProcess();

        StateMachineConfig processConfig = configRepository
                .findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.PROCESS,
                        process.getProcessType(),
                        process.getConfigVersion())
                .orElseThrow(() -> new IllegalStateException("Process config not found"));

        TransitionContext processContext = new TransitionContext(
                process,
                context.actorId(),
                ActorType.SYSTEM,
                Map.of());

        TransitionResult result = transitionEngine.transition(
                EntityType.PROCESS,
                process.getId(),
                processConfig.getId(),
                process.getStatus(),
                TriggerType.SYSTEM_ACTION,
                processContext,
                process::setStatus);

        if (!result.performed()) {
            // no-op: возможно, процесс уже OnRework
        }
    }
}
