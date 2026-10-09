package ru.coordination.approval.service.guard;

import org.springframework.stereotype.Component;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * Отличает отзыв процесса от возобновления.
 *
 * <p>Из состояния {@code OnRework} по {@code USER_ACTION} доступны два перехода: {@code ResumeProcess}
 * и {@code RecallProcess}. ModelFactory сортирует кандидатов по {@code priority}, затем по {@code code},
 * поэтому одна пара «(from_state, trigger) → два перехода с разными намерениями» делает выбор
 * недетерминированным по смыслу: без явного различения возобновление могло уйти в отзыв
 * (и наоборот) — именно это происходило в {@code ProcessResumeIntegrationTest}.
 *
 * <p>Намерение передаётся параметром перехода: {@code ProcessService.recall} кладёт {@code reason},
 * {@code ProcessService.resume} — {@code targetStageOrderIdx}. Guard проверяет наличие {@code reason},
 * поэтому {@code RecallProcess} выполняется только при явном отзыве.
 */
@Component("isRecallRequestGuard")
public class IsRecallRequestGuard implements Guard {

    @Override
    public boolean evaluate(TransitionContext context) {
        return context.parameters().get("reason") != null;
    }
}
