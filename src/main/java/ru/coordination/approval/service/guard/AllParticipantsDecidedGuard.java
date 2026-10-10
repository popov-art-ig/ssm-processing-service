package ru.coordination.approval.service.guard;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.DecisionRepository;
import ru.coordination.approval.domain.process.ParticipantRepository;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * «Все участники текущей итерации приняли решение» — предусловие агрегации решений этапа
 * ({@code 05_guards_actions_registry.md} §4.2, режим {@code evaluateAggregation}).
 *
 * <p><b>Почему источник истины — записи {@code decision}, а не {@code participant.status}.</b>
 * Guard вызывается как action перехода {@code Decide} (через
 * {@link ru.coordination.approval.service.action.EvaluateAggregationAction}), а
 * {@code TransitionEngine} записывает новый статус сущности <i>после</i> выполнения всех
 * actions перехода. Поэтому в момент проверки у только что проголосовавшего участника статус
 * в БД ещё {@code Assigned} — даже если читать его отдельным запросом. Наличие строки в
 * {@code decision} такой проблемы не имеет: {@code RecordDecision} выполняется первым действием.
 *
 * <p>Дополнительно коллекция {@code stage.getIterations().get(..).getParticipants()} в этой же
 * транзакции может содержать устаревшие экземпляры, поэтому она не используется: количество
 * участников берётся из репозитория.
 */
@Component("allParticipantsDecidedGuard")
@RequiredArgsConstructor
public class AllParticipantsDecidedGuard implements Guard {

    private final ParticipantRepository participantRepository;
    private final DecisionRepository decisionRepository;

    /**
     * Логика без обращения к БД — вынесена отдельно для юнит-теста.
     *
     * <p>Сравниваются «число остальных участников» и «число решений остальных участников»:
     * сущность текущего перехода исключается из обоих счётчиков, потому что
     * {@code TransitionEngine} записывает её новый статус до выполнения actions и она уже
     * выглядит как проголосовавшая.
     */
    static boolean allOthersDecided(long otherParticipantCount, long otherDecisionCount) {
        return otherParticipantCount == 0 || otherDecisionCount >= otherParticipantCount;
    }

    /**
     * @return {@code true}, если все участники текущей итерации, кроме инициатора перехода,
     *         уже приняли решение (или участников кроме него не осталось); {@code false} — если
     *         итерации нет, участников нет, инициатор не определён или кто-то ещё не решил
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

        UUID iterationId = currentIteration.getId();

        // Голосующий участник — инициатор перехода ({@code TransitionContext.actorId}); его решение
        // уже записано и его статус уже изменён движком, поэтому он исключается из обоих счётчиков.
        UUID currentParticipantId = context.actorId();

        // Всего участников должно быть хотя бы один, иначе агрегировать нечего.
        if (participantRepository.countByStageIterationId(iterationId) == 0) {
            return false;
        }

        if (currentParticipantId == null) {
            return false;
        }

        long otherParticipants =
                participantRepository.countByStageIterationIdExcluding(iterationId, currentParticipantId);
        long otherDecisions =
                decisionRepository.countByStageIterationIdExcluding(iterationId, currentParticipantId);

        return allOthersDecided(otherParticipants, otherDecisions);
    }
}
