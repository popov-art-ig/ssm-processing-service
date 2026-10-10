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
 * G-S-003 «Агрегация решений: Согласовано с замечаниями» — предусловие перехода
 * {@code StageApprovedWithComments} ({@code Active → ApprovedWithComments}) этапа
 * ({@code 05_guards_actions_registry.md} §4.2). Резолвится {@code guard_registry.handler =
 * 'aggregationApprovedWithCommentsGuard'} (миграция V21).
 *
 * <p><b>Контракт.</b> {@code true} — агрегация даёт {@code APPROVED_WITH_COMMENTS}; {@code false}
 * — решений нет, режим даёт другой результат или среди решений нет ни одного
 * {@code APPROVE_WITH_COMMENTS}. Побочных эффектов нет; статус пишет {@code TransitionEngine}.
 *
 * <p><b>Почему нужны два условия.</b> Переходы {@code StageApproved} и
 * {@code StageApprovedWithComments} отличаются только одним guard'ом, поэтому недостаточно
 * проверить «есть замечания»: нужно ещё, чтобы режим агрегации в принципе допускал
 * согласование. Иначе этап с единственным {@code REJECT} закрывался бы как
 * {@code ApprovedWithComments}.
 *
 * <p><b>Отклонения от {@code 05_guards_actions_registry.md} §4.2.</b> Общая функция
 * {@code evaluateAggregation(stage)} не выделена (логика скопирована в приватный метод
 * {@code evaluateLikeAggregationApproved} вместо переиспользования
 * {@link AggregationApprovedGuard}), и по режимам:
 * <ul>
 *   <li>в режиме {@code ANY_APPROVE} спецификация даёт {@code APPROVED_WITH_COMMENTS}, если
 *       первый по {@code decidedAt} положительный голос — {@code APPROVE_WITH_COMMENTS}; здесь
 *       дополнительно требуется, чтобы все решения были положительными, из-за чего этап с
 *       голосами «Согласовано с замечаниями + Отклонено» не закрывается ни одним из трёх
 *       переходов;</li>
 *   <li>при единственном решении {@code APPROVE_WITH_COMMENTS} в режиме {@code AND} условия
 *       «все положительные» и «есть {@code APPROVE_WITH_COMMENTS}» выполняются одновременно —
 *       этап получает статус «с замечаниями», хотя чистого согласования не было;</li>
 *   <li>{@code FIRST_REJECT_FAIL_FAST} обработан как «все положительные», без приоритета
 *       {@code REJECT}.</li>
 * </ul>
 *
 * <p>Выбор между этим переходом и {@code StageApproved} закреплён миграцией V21 на уровне данных:
 * у {@code StageApprovedWithComments} {@code priority = 10}, потому что {@code ModelFactory}
 * сортирует кандидатов по {@code priority}, а затем по {@code code}, и при равном приоритете
 * победил бы лексикографически меньший {@code StageApproved}.
 */
@Component("aggregationApprovedWithCommentsGuard")
@RequiredArgsConstructor
public class AggregationApprovedWithCommentsGuard implements Guard {

    private final DecisionRepository decisionRepository;

    /**
     * @return {@code true}, если режим агрегации допускает согласование и среди решений есть
     *         хотя бы одно {@code APPROVE_WITH_COMMENTS}
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

        if (!evaluateLikeAggregationApproved(stage, decisions)) {
            return false;
        }

        return decisions.stream().anyMatch(d -> "APPROVE_WITH_COMMENTS".equals(d.getResult()));
    }

    /**
     * Копия режимной части {@link AggregationApprovedGuard} с расширенным набором положительных
     * результатов ({@code APPROVE} или {@code APPROVE_WITH_COMMENTS}).
     */
    private boolean evaluateLikeAggregationApproved(StageInstance stage, List<Decision> decisions) {
        DecisionMode mode = stage.getDecisionMode() != null ? stage.getDecisionMode() : DecisionMode.AND;

        return switch (mode) {
            case AND -> decisions.stream().allMatch(d -> "APPROVE".equals(d.getResult()) || "APPROVE_WITH_COMMENTS".equals(d.getResult()));
            case ANY_APPROVE -> decisions.stream().anyMatch(d -> "APPROVE".equals(d.getResult()) || "APPROVE_WITH_COMMENTS".equals(d.getResult()));
            case ANY_REJECT -> decisions.stream().noneMatch(d -> "REJECT".equals(d.getResult()));
            case ANY_DECISION -> true;
            case FIRST_REJECT_FAIL_FAST -> decisions.stream().allMatch(d -> "APPROVE".equals(d.getResult()) || "APPROVE_WITH_COMMENTS".equals(d.getResult()));
        };
    }
}
