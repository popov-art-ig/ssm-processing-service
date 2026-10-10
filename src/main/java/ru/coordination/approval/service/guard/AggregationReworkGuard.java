package ru.coordination.approval.service.guard;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.common.DecisionMode;
import ru.coordination.approval.domain.process.Decision;
import ru.coordination.approval.domain.process.DecisionRepository;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-S-004 «Агрегация решений: На доработку» — предусловие перехода {@code StageOnRework}
 * ({@code Active → OnRework}) этапа ({@code 05_guards_actions_registry.md} §4.2). Резолвится
 * {@code guard_registry.handler = 'aggregationReworkGuard'} (миграция V21).
 *
 * <p><b>Контракт.</b> {@code true} — агрегация даёт {@code REWORK}, то есть этап закрывается в
 * {@code OnRework}; {@code false} — решений нет или режим даёт другой результат. Побочных
 * эффектов нет; статус пишет {@code TransitionEngine}.
 *
 * <p><b>Почему {@code REWORK} ведёт в {@code OnRework}, а не в терминальный {@code Rejected}.</b>
 * Это правило спецификации ({@code 05_guards_actions_registry.md} §4.2, «Важно»): этап не может
 * быть закрыт с итогом «Отклонён», если он идёт на новую итерацию. Терминальный {@code Rejected}
 * доступен только при возврате на более ранний этап — его ставит
 * {@link ru.coordination.approval.service.action.RejectStagesFromAction} (A-RT-002), а не этот
 * guard.
 *
 * <p><b>Отклонения от {@code 05_guards_actions_registry.md} §4.2.</b> Общая функция
 * {@code evaluateAggregation(stage)} не выделена, и в режиме {@code ANY_APPROVE} условие
 * инвертировано: вместо «есть хотя бы одно {@code REJECT}» проверяется «все решения
 * {@code REJECT}». Тот же режим в {@link AggregationApprovedGuard} требует наличия
 * {@code APPROVE}, поэтому при голосах «Согласовано + Отклонено» оба guard'а дают {@code false},
 * и агрегация ({@link ru.coordination.approval.service.action.EvaluateAggregationAction}) не
 * закрывает этап ни одним из трёх переходов — этап остаётся {@code Active}.
 *
 * <p>Режим {@code ANY_DECISION} здесь всегда даёт {@code false}, что согласуется со
 * спецификацией только частично: по спеке первое по {@code decidedAt} решение {@code REJECT}
 * должно давать {@code REWORK}, но в {@link AggregationApprovedGuard} этот же режим возвращает
 * {@code true} при любом непустом списке решений.
 */
@Component("aggregationReworkGuard")
@RequiredArgsConstructor
public class AggregationReworkGuard implements Guard {

    private final DecisionRepository decisionRepository;

    /**
     * @return {@code true}, если по текущему режиму агрегации этап должен уйти в
     *         {@code OnRework}; {@code false}, если решений нет или режим даёт иной результат
     */
    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof StageInstance stage)) {
            return false;
        }

        StageIteration currentIteration = stage.getIterations().stream()
                .max((a, b) -> Integer.compare(a.getIterationIdx(), b.getIterationIdx()))
                .orElse(null);

        if (currentIteration == null) {
            return false;
        }

        List<Participant> participants = currentIteration.getParticipants();
        List<Decision> decisions = participants.stream()
                .map(p -> decisionRepository.findByParticipantId(p.getId()).orElse(null))
                .filter(d -> d != null)
                .collect(java.util.stream.Collectors.toList());

        if (decisions.isEmpty()) {
            return false;
        }

        DecisionMode mode = stage.getDecisionMode() != null ? stage.getDecisionMode() : DecisionMode.AND;

        return switch (mode) {
            case AND -> decisions.stream().anyMatch(d -> "REJECT".equals(d.getResult()));
            case ANY_APPROVE -> decisions.stream().allMatch(d -> "REJECT".equals(d.getResult()));
            case ANY_REJECT -> decisions.stream().anyMatch(d -> "REJECT".equals(d.getResult()));
            case ANY_DECISION -> false;
            case FIRST_REJECT_FAIL_FAST -> decisions.stream().anyMatch(d -> "REJECT".equals(d.getResult()));
        };
    }
}
