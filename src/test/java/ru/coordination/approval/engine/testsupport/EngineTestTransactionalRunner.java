package ru.coordination.approval.engine.testsupport;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.statemachine.TriggerType;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.TransitionEngine;
import ru.coordination.approval.engine.TransitionResult;

/**
 * Прогоняет {@link TransitionEngine#transition} в собственной транзакции — так же, как это
 * будет делать реальный вызывающий код (Domain Core сервис в следующей фазе): загружает
 * managed-сущность и передаёт {@code target::setStatus} в движок, так что flush/commit
 * (и, соответственно, оптимистичная блокировка) происходит на границе этого метода, а не
 * внутри {@code TransitionEngine} (PHASE-02, T7/T8).
 */
@Component
@Profile("test")
@RequiredArgsConstructor
public class EngineTestTransactionalRunner {

    private final EngineTestTargetRepository targetRepository;
    private final TransitionEngine transitionEngine;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public TransitionResult transition(
            UUID targetId, EntityType entityType, UUID configId, TriggerType trigger, TransitionContext context) {
        EngineTestTarget target = targetRepository.findById(targetId).orElseThrow();
        return transitionEngine.transition(
                entityType, targetId, configId, target.getStatus(), trigger, context, target::setStatus);
    }

    /**
     * Как {@link #transition}, но перед вызовом движка эмулирует конкурентное расхождение
     * {@code version} (критерий приёмки 5): отсоединяет загруженную сущность от persistence
     * context, обновляет {@code version} в БД напрямую (минуя Hibernate — имитация уже
     * закоммиченного чужого изменения) и заново присоединяет через {@code merge}. Просто
     * поменять поле {@code version} сеттером на ещё managed-сущности недостаточно — Hibernate
     * использует для WHERE-условия UPDATE собственный снимок версии из persistence context,
     * а не текущее значение поля; расхождение реально проверяется только при {@code merge}
     * detached-экземпляра.
     */
    @Transactional
    public TransitionResult transitionAfterConcurrentVersionBump(
            UUID targetId, EntityType entityType, UUID configId, TriggerType trigger, TransitionContext context) {
        EngineTestTarget target = targetRepository.findById(targetId).orElseThrow();
        entityManager.detach(target);

        entityManager.createNativeQuery("update engine_test_target set version = version + 1 where id = :id")
                .setParameter("id", targetId)
                .executeUpdate();

        EngineTestTarget stale = entityManager.merge(target);
        return transitionEngine.transition(
                entityType, targetId, configId, stale.getStatus(), trigger, context, stale::setStatus);
    }
}
