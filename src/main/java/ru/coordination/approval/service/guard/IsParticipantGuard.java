package ru.coordination.approval.service.guard;

import org.springframework.stereotype.Component;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * <b>Заглушка</b> guard'а «Текущий пользователь — участник процесса» (PHASE-11). Резолвится
 * {@code guard_registry.handler = 'isParticipantGuard'} (миграция V24, код реестра
 * {@code IsParticipant}) и включён в guards переходов {@code CreateRemark}
 * ({@code Draft → Active}) и {@code MarkNotRequired} ({@code Proposed → NotRequired}).
 *
 * <p><b>Что именно не реализовано.</b> {@code evaluate} всегда возвращает {@code true} и не
 * смотрит ни на {@code context.actorId()}, ни на {@code context.entity()}: авторизация создания
 * и закрытия замечания фактически не проверяется, любой аутентифицированный пользователь может
 * создать замечание в процессе. Ограничение осознанное (заглушка PHASE-11), но guard не является
 * рабочей проверкой прав и не должен считаться таковой при ревью карты переходов.
 *
 * <p><b>Расхождения в идентификации.</b> Полная проверка требует обращения к составу участников
 * процесса через {@code ProcessRepository} — это отдельная задача. Кроме того, приписанный в
 * реестре V24 код {@code G-R-001} и javadoc-заголовок «G-R-001» не соответствуют спецификации:
 * в {@code 05_guards_actions_registry.md} §4.3 код {@code G-PA-001} принадлежит условию
 * {@code IsParticipant}, а код {@code G-R-001} (§4.6) — условию {@code ParticipantNotDecided},
 * которое здесь не реализуется.
 */
@Component("isParticipantGuard")
public class IsParticipantGuard implements Guard {

    /** @return всегда {@code true} — заглушка, см. описание класса */
    @Override
    public boolean evaluate(TransitionContext context) {
        return true;
    }
}
