package ru.coordination.approval.service.guard;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageRepository;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-S-001 «Предыдущий этап имеет финальный статус» (PHASE-04, источник:
 * {@code 05_guards_actions_registry.md} §4.2). Резолвится {@code guard_registry.handler =
 * 'previousStageCompletedGuard'} (миграция V19).
 *
 * <p>Спецификация не описывает случай «предыдущего этапа не существует» (первый этап
 * маршрута, минимальный {@code orderIdx}) — тикет явно решает: guard проходит ({@code true}),
 * блокировать нечего. Без этого допущения {@code StartProcess} не смог бы активировать ни
 * один процесс.
 *
 * <p><b>Почему статус читается из репозитория.</b> Guard вызывается как action перехода
 * закрытия этапа ({@code ActivateNextStageAction} внутри {@code StageApproved}), то есть ДО
 * того, как {@code TransitionEngine} запишет закрываемому этапу новый статус. В памяти
 * {@code process.getStages()} предыдущий этап в этот момент ещё {@code Active}, поэтому
 * проверка по коллекции всегда возвращала {@code false} и следующий этап не активировался.
 * Запрос к БД видит статус, уже записанный в рамках текущей транзакции.
 */
@Component("previousStageCompletedGuard")
@RequiredArgsConstructor
public class PreviousStageCompletedGuard implements Guard {

    private static final Set<String> COMPLETED_STATUSES = Set.of("Approved", "ApprovedWithComments", "Completed");

    private final StageRepository stageRepository;

    /**
     * @return {@code true}, если предыдущий этап маршрута уже в финальном статусе, либо если
     *         предыдущего этапа нет (первый этап); {@code false}, если он ещё не завершён или не
     *         найден в БД
     */
    @Override
    public boolean evaluate(TransitionContext context) {
        StageInstance stage = (StageInstance) context.entity();

        List<UUID> previousStageIds = stageRepository.findPreviousStageIds(
                stage.getProcess().getId(), stage.getOrderIdx());

        if (previousStageIds.isEmpty()) {
            return true; // первый этап маршрута — блокировать нечего
        }

        return stageRepository.findById(previousStageIds.get(0))
                .map(previous -> COMPLETED_STATUSES.contains(previous.getStatus()))
                .orElse(false);
    }
}
