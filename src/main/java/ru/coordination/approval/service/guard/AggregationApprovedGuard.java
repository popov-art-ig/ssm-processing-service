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
 * G-S-002 «Агрегация решений: Согласовано» — предикат предусловия перехода
 * {@code StageApproved} ({@code Active → Approved}, {@code SYSTEM_ACTION}) этапа
 * ({@code 05_guards_actions_registry.md} §4.2). Резолвится {@code guard_registry.handler =
 * 'aggregationApprovedGuard'} (миграция V21), в карте переходов указан как
 * {@code AggregationApproved}.
 *
 * <p><b>Контракт.</b> {@code true} — агрегация решений текущей итерации даёт результат
 * {@code APPROVED}, то есть переход этапа в {@code Approved} разрешён; {@code false} —
 * либо решений ещё нет, либо режим {@code stage.decisionMode} даёт другой результат.
 * Побочных эффектов нет (см. {@link ru.coordination.approval.engine.registry.Guard});
 * статус этапа пишет {@code TransitionEngine} при выполнении перехода.
 *
 * <p><b>Почему источник истины — записи {@code decision}.</b> Источник — строки
 * {@code decision} по участникам текущей (последней по {@code iterationIdx}) итерации, а не
 * {@code participant.status}: {@code TransitionEngine} записывает новый статус сущности до
 * выполнения actions перехода, поэтому у только что проголосовавшего участника статус уже
 * {@code Decided} и по нему нельзя отличить «проголосовал» от «ещё нет» (та же причина, что и
 * в {@link AllParticipantsDecidedGuard}). Проверка «все ли приняли решение» — задача отдельного
 * guard'а: этот отвечает только за режим агрегации.
 *
 * <p><b>Отклонения от {@code 05_guards_actions_registry.md} §4.2.</b> Спецификация описывает три
 * guard'а G-S-002…G-S-004 как «три тонких предиката поверх одной общей функции
 * {@code evaluateAggregation(stage)}»; здесь функция не выделена — режимы разобраны
 * {@code switch} инлайн, а отображение «режим → результат» неполное:
 * <ul>
 *   <li>{@code ANY_APPROVE} требует наличия {@code APPROVE}, а не {@code APPROVE} <i>или</i>
 *       {@code APPROVE_WITH_COMMENTS}, как в спецификации (итерация из одних
 *       {@code APPROVE_WITH_COMMENTS} здесь даёт {@code false}, и решение по этапу не
 *       принимается ни этим guard'ом, ни {@link AggregationReworkGuard});</li>
 *   <li>{@code FIRST_REJECT_FAIL_FAST} обработан как {@code AND} (все решения {@code APPROVE}),
 *       без проверки «есть ли {@code REJECT}» до проверки полноты;</li>
 *   <li>режим {@code ANY_DECISION} при непустом списке решений всегда даёт {@code true}, то есть
 *       этап закрывается как {@code Approved} даже тогда, когда первое решение было
 *       {@code REJECT}: спецификация для {@code ANY_DECISION} требует смотреть на первое по
 *       {@code decidedAt} решение и отдавать {@code REWORK} при {@code REJECT}. Порядок решений
 *       не рассматривается вовсе: {@code decision} хранит отметку времени в колонке
 *       {@code recorded_at} ({@link Decision#getRecordedAt()}), тогда как спецификация называет
 *       то же поле {@code decidedAt};</li>
 *   <li>сравнение результата идёт со строкой {@code "APPROVE"}, тогда как
 *       {@code DecisionService.decide} принимает значения вида {@code APPROVE_WITH_COMMENTS}.</li>
 * </ul>
 */
@Component("aggregationApprovedGuard")
@RequiredArgsConstructor
public class AggregationApprovedGuard implements Guard {

    private final DecisionRepository decisionRepository;

    /**
     * @return {@code true}, если по режиму {@code stage.decisionMode} агрегация даёт
     *         «Согласовано»; {@code false}, если решений нет или режим даёт иной результат
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
            case AND -> decisions.stream().allMatch(d -> "APPROVE".equals(d.getResult()));
            case ANY_APPROVE -> decisions.stream().anyMatch(d -> "APPROVE".equals(d.getResult()));
            case ANY_REJECT -> decisions.stream().noneMatch(d -> "REJECT".equals(d.getResult()));
            case ANY_DECISION -> true;
            case FIRST_REJECT_FAIL_FAST -> decisions.stream().allMatch(d -> "APPROVE".equals(d.getResult()));
        };
    }
}
