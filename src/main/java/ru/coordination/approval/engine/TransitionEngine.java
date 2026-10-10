package ru.coordination.approval.engine;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RequiredArgsConstructor
public class TransitionEngine {

    private final ModelFactory modelFactory;
    private final GuardRegistry guardRegistry;
    private final ActionRegistry actionRegistry;
    private final StatusRegistryRepository statusRegistryRepository;
    private final AuditEventRepository auditEventRepository;

    /**
     * Выполняет один переход по алгоритму {@code 04_state_machines.md} §3.3: берёт кандидатов
     * для {@code (fromState, trigger)}, проверяет guards по порядку приоритета и применяет
     * первый переход, все guards которого вернули {@code true}; отказы guards не приводят к
     * ошибке — возвращается «не выполнено» с указанием исходного состояния.
     *
     * @param configId    id конкретной версии {@code StateMachineConfig}, зафиксированной
     *                    вызывающим кодом (Snapshot-on-Start — вне объёма этой фазы); модель
     *                    строится заново на каждый вызов, не кэшируется.
     * @param statusWriter записывает новый {@code status} в сущность вызывающего кода при
     *                    успешном переходе; не вызывается, если переход не выполнен.
     * @return выполненный переход ({@code performed = true} с {@code toState}, кодом перехода и
     *         списком {@code emits}) либо {@link TransitionResult#notPerformed}, если все
     *         кандидаты отклонены guards
     * @throws NoApplicableTransitionException если активных переходов для
     *         {@code (entityType, fromState, trigger)} нет вообще — это ошибка конфигурации, а
     *         не бизнес-отказ
     * @throws InvalidTargetStatusException если {@code toState} выбранного перехода не
     *         зарегистрирован в {@code status_registry} для этого {@code entityType}
     * @throws IllegalArgumentException если карта, загруженная по {@code configId}, описывает
     *         другой {@code entityType}: вызывающий код передал версию конфига от другой машины
     *         состояний, и переход был бы выполнен по чужой карте
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
            String failedGuard = firstFailingGuard(candidate, context);
            if (failedGuard == null) {
                log.debug("transition {} ({} -> {}) chosen for {}", candidate.getCode(),
                        candidate.getFromState(), candidate.getToState(), entityType);
                return execute(entityType, entityId, candidate, context, statusWriter);
            }
            // Диагностика: без неё отказ перехода выглядит как «ничего не произошло» — вызывающий
            // код видит только notPerformed и не знает, какой guard заблокировал переход.
            log.debug("guard '{}' rejected transition '{}' ({} -> {}) for {}",
                    failedGuard, candidate.getCode(), candidate.getFromState(),
                    candidate.getToState(), entityType);
        }
        return TransitionResult.notPerformed(fromState);
    }

    /**
     * Guards перехода проверяются по порядку {@code guards[]} и останавливаются на первом
     * {@code false}: возвращается код отказавшего guard'а, чтобы вызывающий {@link #transition}
     * мог записать его в лог.
     *
     * @return код первого guard'а, вернувшего {@code false}, либо {@code null}, если все прошли
     */
    private String firstFailingGuard(TransitionConfig transition, TransitionContext context) {
        for (String guardCode : transition.getGuards()) {
            Guard guard = guardRegistry.resolve(guardCode);
            if (!guard.evaluate(context)) {
                return guardCode;
            }
        }
        return null;
    }

    /**
     * Применяет выбранный переход — шаги 3b–3c алгоритма: фиксирует новый статус, выполняет
     * actions в порядке {@code actions[]} и пишет {@link AuditEvent}. Все три шага идут в одной
     * транзакции (ADR-028), поэтому частично применённого перехода не бывает.
     */
    private TransitionResult execute(
            EntityType entityType,
            UUID entityId,
            TransitionConfig transition,
            TransitionContext context,
            Consumer<String> statusWriter) {

        validateTargetStatus(entityType, transition.getToState());

        // Новый статус фиксируется ДО выполнения actions: actions переходов закрытия этапа
        // (ActivateNextStageAction, EvaluateProcessCompletionAction) и агрегации
        // (EvaluateAggregationAction) проверяют состояние СВЯЗАННЫХ сущностей — «предыдущий этап
        // завершён», «все этапы завершены», «есть этап на доработке». При записи статуса после
        // actions эти проверки видели старое состояние, и переходы блокировались: следующий этап
        // не активировался, процесс не завершался, ProcessRework не срабатывал.
        //
        // Guards самого перехода уже пройдены (шаг 2 алгоритма) и оценивались по исходному
        // состоянию, поэтому на них смена порядка не влияет. Guard'ы вложенных переходов читают
        // статус связанной сущности из репозитория в той же транзакции (PreviousStageCompletedGuard,
        // AllStagesCompletedGuard), поэтому запись статуса до actions обязательна и для них.
        statusWriter.accept(transition.getToState());

        for (String actionCode : transition.getActions()) {
            Action action = actionRegistry.resolve(actionCode);
            action.execute(context);
        }

        recordAudit(entityType, entityId, transition, context);

        return TransitionResult.performed(
                transition.getFromState(), transition.getToState(), transition.getCode(), transition.getEmits());
    }

    /**
     * Проверяет {@code toState} по реестру статусов: {@code entityType} там хранится строкой
     * ({@code StatusRegistry.entityType}), поэтому передаётся {@code name()} вместо enum —
     * так задано схемой БД (PHASE-02, критерий приёмки 4).
     */
    private void validateTargetStatus(EntityType entityType, String toState) {
        if (!statusRegistryRepository.existsByCodeAndEntityType(toState, entityType.name())) {
            throw new InvalidTargetStatusException(entityType, toState);
        }
    }

    /**
     * Пишет {@link AuditEvent} на каждый выполненный переход (шаг 3c алгоритма, обязателен без
     * исключений — PHASE-02, критерий приёмки 2). В {@code payload} попадают
     * {@code fromState}/{@code toState}/{@code trigger}/{@code emits}: этого достаточно, чтобы
     * восстановить переход по аудиту, не поднимая версию конфига, действовавшую в тот момент.
     */
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
