package ru.coordination.approval.service.action;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.Decision;
import ru.coordination.approval.domain.process.DecisionRepository;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

/**
 * A-PA-001 «Зафиксировать решение» — сохраняет решение участника в {@code decision} (PHASE-05,
 * источник: {@code 05_guards_actions_registry.md} §5.4). Резолвится
 * {@code action_registry.handler = 'recordDecisionAction'} (миграция V20); включён в actions
 * перехода {@code Decide} ({@code Assigned → Decided}, {@code USER_ACTION}) первым — до
 * {@link RecordCommentAction} и {@code EvaluateAggregation}.
 *
 * <p><b>Какое состояние меняет.</b> Добавляет строку в {@code decision} с результатом
 * ({@code APPROVE}/{@code APPROVE_WITH_COMMENTS}/{@code REJECT}), комментарием и
 * {@code auto = false}. Именно эта строка, а не {@code participant.status}, служит источником
 * истины для агрегации: {@code TransitionEngine} записывает статус участника до выполнения
 * actions, поэтому опираться на статус в момент агрегации нельзя (см.
 * {@link ru.coordination.approval.service.guard.AllParticipantsDecidedGuard}).
 *
 * <p><b>Почему запись создаётся здесь, а не движком.</b> {@code decide} в спеке — это и решение
 * участника, и создание записи {@code Decision}; в этом модуле данные решения передаются
 * параметрами триггера ({@code decisionType}, {@code comment}), поэтому действие обязано
 * материализовать их в сущность в той же транзакции.
 *
 * <p><b>Почему {@code id} генерируется здесь.</b> {@code BaseEntity} намеренно объявляет
 * {@code @Id} без {@code @GeneratedValue} (идентификатор задаёт приложение), поэтому {@code id}
 * обязан быть проставлен до {@code save()}: иначе Hibernate падает с «Identifier of entity ...
 * must be manually assigned before calling 'persist()'». Это ломало все сценарии принятия решения
 * (находка сборки).
 *
 * <p>Корректность {@code decisionType} и обязательность комментария для
 * {@code APPROVE_WITH_COMMENTS}/{@code REJECT} проверяет {@code DecisionService.decide} до вызова
 * движка, поэтому действие не валидирует параметры и не бросает исключений; опубликованное в
 * §5.4 событие {@code approval.decision.recorded} отдано {@code transition_config.emits} и Event
 * Publisher (PHASE-19).
 */
@Component("recordDecisionAction")
@RequiredArgsConstructor
public class RecordDecisionAction implements Action {

    private final DecisionRepository decisionRepository;

    /**
     * Сохраняет решение участника.
     *
     * @param context контекст перехода {@code Decide}; параметры {@code decisionType}
     *                (обязателен, валидируется вызывающим кодом) и {@code comment}
     *                (необязателен)
     */
    @Override
    public void execute(TransitionContext context) {
        Participant participant = (Participant) context.entity();

        String decisionResult = (String) context.parameters().get("decisionType");
        String comment = (String) context.parameters().get("comment");

        Decision decision = Decision.builder()
                // BaseEntity намеренно объявляет @Id без @GeneratedValue (генерирует приложение),
                // поэтому id обязан быть проставлен до save(): иначе Hibernate падает с
                // "Identifier of entity ... must be manually assigned before calling 'persist()'".
                // Это ломало все сценарии принятия решения (находка сборки).
                .id(UUID.randomUUID())
                .participant(participant)
                .result(decisionResult)
                .comment(comment)
                .auto(false)
                .recordedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        decisionRepository.save(decision);
    }
}
