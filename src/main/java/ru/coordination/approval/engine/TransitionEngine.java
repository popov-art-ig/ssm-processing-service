package ru.coordination.approval.engine;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.coordination.approval.domain.audit.AuditEvent;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.statemachine.TransitionConfig;
import ru.coordination.approval.domain.statemachine.TriggerType;
import ru.coordination.approval.engine.exception.InvalidTargetStatusException;
import ru.coordination.approval.engine.exception.NoApplicableTransitionException;
import ru.coordination.approval.engine.model.ModelFactory;
import ru.coordination.approval.engine.model.TransitionModel;
import ru.coordination.approval.engine.registry.Action;
import ru.coordination.approval.engine.registry.ActionRegistry;
import ru.coordination.approval.engine.registry.Guard;
import ru.coordination.approval.engine.registry.GuardRegistry;

/**
 * Собственный движок машины состояний (ADR-028), заменяющий Spring State Machine. Реализует
 * алгоритм PHASE-02 §2 дословно: найти переходы по {@code (entityType, fromState, trigger)}
 * → отсортировать по priority → для каждого проверить guards → если все true, выполнить
 * actions, сменить статус, записать {@link AuditEvent}.
 *
 * <p>Пишет {@code status} напрямую в сущность вызывающего кода через {@code statusWriter}
 * (например, {@code process::setStatus}) — минимальный функциональный контракт вместо
 * интерфейса {@code HasStatus}, чтобы не модифицировать сущности {@code domain.process.*}
 * (см. «Открытые вопросы» тикета и {@code doc/tasks/PHASE-02-tasks.md}). Оптимистичная
 * блокировка (критерий приёмки 5) не реализуется вручную — используется штатный
 * {@code @Version} сущности вызывающего кода: {@code statusWriter} лишь мутирует managed-
 * сущность, flush/commit происходит на границе {@code @Transactional}, и любой
 * {@code OptimisticLockException} на этой границе не перехватывается здесь и уходит
 * вызывающему коду как есть.
 *
 * <p>Не хранит сериализованное состояние/контекст — {@code status} сущности является
 * единственным персистентным представлением текущего состояния (ADR-028).
 */
@Service
@RequiredArgsConstructor
public class TransitionEngine {

    private final ModelFactory modelFactory;
    private final GuardRegistry guardRegistry;
    private final ActionRegistry actionRegistry;
    private final StatusRegistryRepository statusRegistryRepository;
    private final AuditEventRepository auditEventRepository;

    /**
     * @param configId    id конкретной версии {@code StateMachineConfig}, зафиксированной
     *                    вызывающим кодом (Snapshot-on-Start — вне объёма этой фазы); модель
     *                    строится заново на каждый вызов, не кэшируется.
     * @param statusWriter записывает новый {@code status} в сущность вызывающего кода при
     *                    успешном переходе; не вызывается, если переход не выполнен.
     */
    @Transactional
    public TransitionResult transition(
            EntityType entityType,
            UUID entityId,
            UUID configId,
            String fromState,
            TriggerType trigger,
            TransitionContext context,
            Consumer<String> statusWriter) {

        TransitionModel model = modelFactory.loadModel(configId);
        if (model.entityType() != entityType) {
            throw new IllegalArgumentException(
                    "StateMachineConfig %s is for entityType=%s, requested entityType=%s"
                            .formatted(configId, model.entityType(), entityType));
        }

        List<TransitionConfig> candidates = model.candidates(fromState, trigger);
        if (candidates.isEmpty()) {
            throw new NoApplicableTransitionException(entityType, fromState, trigger);
        }

        for (TransitionConfig candidate : candidates) {
            if (guardsPass(candidate, context)) {
                return execute(entityType, entityId, candidate, context, statusWriter);
            }
        }
        return TransitionResult.notPerformed(fromState);
    }

    private boolean guardsPass(TransitionConfig transition, TransitionContext context) {
        for (String guardCode : transition.getGuards()) {
            Guard guard = guardRegistry.resolve(guardCode);
            if (!guard.evaluate(context)) {
                return false;
            }
        }
        return true;
    }

    private TransitionResult execute(
            EntityType entityType,
            UUID entityId,
            TransitionConfig transition,
            TransitionContext context,
            Consumer<String> statusWriter) {

        validateTargetStatus(entityType, transition.getToState());

        for (String actionCode : transition.getActions()) {
            Action action = actionRegistry.resolve(actionCode);
            action.execute(context);
        }

        statusWriter.accept(transition.getToState());
        recordAudit(entityType, entityId, transition, context);

        return TransitionResult.performed(
                transition.getFromState(), transition.getToState(), transition.getCode(), transition.getEmits());
    }

    private void validateTargetStatus(EntityType entityType, String toState) {
        if (!statusRegistryRepository.existsByCodeAndEntityType(toState, entityType.name())) {
            throw new InvalidTargetStatusException(entityType, toState);
        }
    }

    private void recordAudit(
            EntityType entityType, UUID entityId, TransitionConfig transition, TransitionContext context) {
        AuditEvent event = AuditEvent.builder()
                .id(UUID.randomUUID())
                .entityType(entityType.name())
                .entityId(entityId)
                .action(transition.getCode())
                .actorId(context.actorId())
                .actorType(context.actorType())
                .payload(Map.of(
                        "fromState", transition.getFromState(),
                        "toState", transition.getToState(),
                        "trigger", transition.getTrigger().name(),
                        "emits", transition.getEmits()))
                .occurredAt(Instant.now())
                .build();
        auditEventRepository.save(event);
    }
}
