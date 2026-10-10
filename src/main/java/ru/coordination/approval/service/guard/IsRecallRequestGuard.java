package ru.coordination.approval.service.guard;

import org.springframework.stereotype.Component;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * Отличает отзыв процесса от возобновления (PHASE-08/PHASE-09). Резолвится
 * {@code guard_registry.handler = 'isRecallRequestGuard'} (миграция V23, код реестра
 * {@code IsRecallRequest}); включён в guards обоих переходов {@code RecallProcess}
 * ({@code InProgress → Recalled} и {@code OnRework → Recalled}, {@code USER_ACTION}).
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
 *
 * <p><b>Кода в спецификации нет.</b> В {@code 05_guards_actions_registry.md} такого условия нет:
 * разделение намерений — следствие data-driven модели, где переход выбирается по паре
 * «состояние + триггер», а не по вызываемому методу. Побочных эффектов нет.
 */
@Component("isRecallRequestGuard")
public class IsRecallRequestGuard implements Guard {

    /** @return {@code true}, если в параметрах перехода передан {@code reason} (это отзыв) */
    @Override
    public boolean evaluate(TransitionContext context) {
        return context.parameters().get("reason") != null;
    }
}
