package ru.coordination.approval.engine;

import java.util.Map;
import java.util.UUID;
import ru.coordination.approval.domain.audit.ActorType;

/**
 * Данные, доступные guard/action во время выполнения перехода (PHASE-02, раздел 4).
 *
 * <p>Точный состав не зафиксирован ни одним документом («Открытые вопросы» п.2 тикета) —
 * это минимально достаточный контракт для guard/action-заглушек этой фазы. Ожидаемо
 * расширится в фазах, реализующих конкретные guards/actions (доступ к адаптерам и т.п.).
 *
 * @param entity     сущность, над которой выполняется переход (например, {@code
 *                   ProcessInstance}) — тип не фиксируется движком, guard/action приводят
 *                   его к ожидаемому типу сами.
 * @param actorId    инициатор перехода; {@code null} для системных/таймерных триггеров без
 *                   привязки к пользователю.
 * @param actorType  тип инициатора — пишется как есть в {@code AuditEvent.actorType}.
 * @param parameters произвольные параметры триггера (например, поля формы решения).
 */
public record TransitionContext(
        Object entity,
        UUID actorId,
        ActorType actorType,
        Map<String, Object> parameters) {

    public TransitionContext {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
