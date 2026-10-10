package ru.coordination.approval.service.action;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.domain.statemachine.TriggerType;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.TransitionEngine;
import ru.coordination.approval.engine.TransitionResult;
import ru.coordination.approval.engine.model.StateMachineConfigRepository;
import ru.coordination.approval.engine.registry.Action;

/**
 * A-RT-005 «Активировать целевой этап»: создаёт новую итерацию целевого этапа, клонирует в неё
 * участников прошлой итерации и активирует этап вложенным переходом
 * ({@code OnRework/Approved/ApprovedWithComments → Active}, {@code ReactivateStage}) (PHASE-08,
 * источник: {@code 05_guards_actions_registry.md} §5.3). Резолвится
 * {@code action_registry.handler = 'activateTargetStageAction'} (миграция V23); включён в actions
 * перехода {@code ResumeProcess} ({@code OnRework → InProgress}, {@code USER_ACTION}), всегда
 * после {@link RejectStagesFromAction}.
 *
 * <p><b>Какое состояние меняет.</b> Добавляет в целевому этапу новую
 * {@link StageIteration} со статусом {@code Active} и клонами участников в статусе
 * {@code Pending} ({@code assignedAt}, {@code dueAt} — {@code null}), а затем переводит сам этап
 * в {@code Active} явным переходом state machine — прямое присваивание {@code stage.status} не
 * используется, поскольку состояние меняется только через переход (ADR-002/ADR-028, тот же
 * принцип, что и в {@link AssignStageTasksAction}).
 *
 * <p><b>Почему именно {@code SYSTEM_ACTION} и та же транзакция.</b> Контекст вложенного перехода
 * создаётся от имени {@code ActorType.SYSTEM} с копией {@code actorId} вызывающего кода, а вызов
 * {@link TransitionEngine#transition} идёт в той же транзакции, что и внешний {@code ResumeProcess}
 * ({@code PROPAGATION.REQUIRED} по умолчанию) — если этап не активируется, откатывается весь
 * возврат из доработки.
 *
 * <p><b>Почему {@code notPerformed} — ошибка, а не тихий no-op.</b> К этому моменту переходы
 * {@code ReactivateStage} объявлены без guards (V23) и отказ возможен только при нарушении
 * конфигурации; молчаливое продолжение оставило бы процесс {@code InProgress} с неактивным
 * целевым этапом, то есть процесс «завис» бы без единого активного этапа.
 *
 * <p><b>Упрощение.</b> Дополнительные согласующие не клонируются — список
 * {@code additionalApprovers} у новой итерации пуст (PHASE-08), иерархия доп. согласующих
 * восстанавливается в PHASE-10. Клонирование участников копирует {@code userId},
 * {@code organizationId}, {@code role}, {@code actorSlotRef}, {@code resolvedRoleRef} и
 * {@code orderIdx}, но не {@code participant.decision}: решения предыдущей итерации в новую
 * попытку не переносятся.
 */
@Component("activateTargetStageAction")
@RequiredArgsConstructor
public class ActivateTargetStageAction implements Action {

    private final StateMachineConfigRepository configRepository;
    private final TransitionEngine transitionEngine;

    /**
     * Создаёт новую итерацию целевого этапа с клонами участников и переводит этап в
     * {@code Active}.
     *
     * @param context контекст перехода {@code ResumeProcess}; обязательный параметр —
     *                {@code targetStageOrderIdx} (индекс целевого этапа), его отсутствие
     *                приводит к {@link IllegalArgumentException}
     * @throws IllegalArgumentException если этап с переданным {@code targetStageOrderIdx} в
     *         процессе не найден
     * @throws IllegalStateException если этап не активировался ({@code performed == false}) или у
     *         него нет ни одной итерации для клонирования участников
     */
    @Override
    public void execute(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();
        Integer targetStageOrderIdx = (Integer) context.parameters().get("targetStageOrderIdx");

        StageInstance targetStage = process.getStages().stream()
                .filter(s -> s.getOrderIdx().equals(targetStageOrderIdx))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Target stage not found"));

        Integer maxIterationIdx = targetStage.getIterations().stream()
                .map(StageIteration::getIterationIdx)
                .max(Integer::compareTo)
                .orElse(0);

        StageIteration lastIteration = targetStage.getIterations().stream()
                .filter(it -> it.getIterationIdx().equals(maxIterationIdx))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No iterations found"));

        StageIteration newIteration = StageIteration.builder()
                .id(UUID.randomUUID())
                .stage(targetStage)
                .iterationIdx(maxIterationIdx + 1)
                .status("Active")
                .startedAt(Instant.now())
                .createdAt(Instant.now())
                .participants(new ArrayList<>())
                .build();

        for (Participant oldParticipant : lastIteration.getParticipants()) {
            Participant newParticipant = Participant.builder()
                    .id(UUID.randomUUID())
                    .stageIteration(newIteration)
                    .userId(oldParticipant.getUserId())
                    .organizationId(oldParticipant.getOrganizationId())
                    .role(oldParticipant.getRole())
                    .actorSlotRef(oldParticipant.getActorSlotRef())
                    .resolvedRoleRef(oldParticipant.getResolvedRoleRef())
                    .orderIdx(oldParticipant.getOrderIdx())
                    .status("Pending")
                    .assignedAt(null)
                    .dueAt(null)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            newIteration.getParticipants().add(newParticipant);
        }

        targetStage.getIterations().add(newIteration);

        StateMachineConfig stageConfig = configRepository
                .findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.STAGE,
                        process.getProcessType(),
                        process.getConfigVersion())
                .orElseThrow(() -> new IllegalStateException("Stage config not found"));

        TransitionContext stageContext = new TransitionContext(
                targetStage,
                context.actorId(),
                ActorType.SYSTEM,
                Map.of());

        TransitionResult result = transitionEngine.transition(
                EntityType.STAGE,
                targetStage.getId(),
                stageConfig.getId(),
                targetStage.getStatus(),
                TriggerType.SYSTEM_ACTION,
                stageContext,
                targetStage::setStatus);

        if (!result.performed()) {
            throw new IllegalStateException(
                    "Failed to activate target stage " + targetStage.getId() +
                            " (orderIdx=" + targetStage.getOrderIdx() + ")");
        }
    }
}
