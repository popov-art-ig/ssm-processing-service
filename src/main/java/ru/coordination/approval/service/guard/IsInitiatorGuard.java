package ru.coordination.approval.service.guard;

import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-P-001 «Текущий пользователь — инициатор процесса» (PHASE-03, источник:
 * {@code 05_guards_actions_registry.md} §4.1). Резолвится {@code guard_registry.handler =
 * 'isInitiatorGuard'} (миграция V18); включён в guards переходов {@code StartProcess}
 * ({@code Draft → InProgress}), {@code RecallProcess} ({@code InProgress → Recalled} и
 * {@code OnRework → Recalled}, V23) и {@code ResumeProcess} ({@code OnRework → InProgress}, V23).
 *
 * <p><b>Контракт</b> — единственное условие авторизации этих переходов: {@code true}, если
 * {@code context.actorId()} совпадает с {@code process.initiatorId}; {@code false} иначе, в том
 * числе для системного триггера без {@code actorId}. Инициатор процесса объявлен единственной
 * ролью, которой разрешены запуск, отзыв и возобновление, — следствие того, что
 * {@code G-P-002 IsResponsible} и {@code G-P-003 IsAdmin} (§4.1) не реализованы.
 *
 * <p>Побочных эффектов нет; guard не различает {@code STANDARD} и {@code UNIFIED} — проверка
 * роли ответственного для UNIFIED появится вместе с PHASE-12/PHASE-13.
 */
@Component("isInitiatorGuard")
public class IsInitiatorGuard implements Guard {

    /** @return {@code true}, если переход инициирует инициатор процесса */
    @Override
    public boolean evaluate(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();
        return context.actorId() != null && context.actorId().equals(process.getInitiatorId());
    }
}
