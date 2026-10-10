package ru.coordination.approval.service;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.ProcessRepository;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.domain.statemachine.TriggerType;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.TransitionEngine;
import ru.coordination.approval.engine.TransitionResult;
import ru.coordination.approval.engine.model.StateMachineConfigRepository;

/**
 * Use-case сервис для принятия решения участником (PHASE-05).
 *
 * <p>Работает с агрегатом процесса: меняет статус {@code Participant}, а запись решения и
 * комментария выполняют actions перехода. Решение участника — единственный вход в агрегацию этапа:
 * переход {@code Decide} ({@code Assigned -> Decided}) несёт actions
 * {@code RecordDecision,RecordComment,EvaluateAggregation} (миграция V20), и последний action может
 * в той же транзакции закрыть этап переходами {@code StageApproved}/
 * {@code StageApprovedWithComments}/{@code StageOnRework} (PHASE-06, миграция V21). Набор голосов
 * считается по {@code stage.decisionMode} (ADR-022, {@code 05_guards_actions_registry.md} §4.2).
 *
 * <p>Комментарий доп. согласующего сюда не попадает: его рекомендация не участвует ни в агрегации,
 * ни в переходах (ADR-016, {@code 07_api_contract.md} §15.5).
 */
@Service
@RequiredArgsConstructor
public class DecisionService {

    private final ProcessRepository processRepository;
    private final StateMachineConfigRepository stateMachineConfigRepository;
    private final TransitionEngine transitionEngine;

    /**
     * Записывает решение участника текущей (последней) итерации этапа: {@code Assigned -> Decided}
     * по триггеру {@code USER_ACTION} в карте {@code PARTICIPANT} той версии, которая зафиксирована
     * в процессе (Snapshot-on-Start, ADR-003).
     *
     * <p><b>Предусловия.</b> Участник должен существовать в последней итерации этапа и находиться в
     * статусе {@code Assigned}; {@code APPROVE_WITH_COMMENTS} и {@code REJECT} требуют непустого
     * комментария — проверка выполняется до обращения к движку, чтобы не оставлять частичных
     * изменений.
     *
     * <p><b>Что меняется после вызова.</b> Статус участника, строка {@code decision} и (при
     * непустом комментарии) строка {@code comment}; при этом {@code EvaluateAggregation} может
     * закрыть этап — статус этапа и {@code completedAt} меняются в рамках того же вызова.
     *
     * <p><b>Известные расхождения.</b> {@code 07_api_contract.md} §16.1 допускает для {@code REJECT}
     * «комментарий ИЛИ замечание», здесь обязателен именно комментарий (решение открытого вопроса 2
     * тикета PHASE-11 — расширение отложено). Повторный вызов на участнике в статусе {@code Decided}
     * даёт {@link IllegalStateException} из явной проверки статуса, а не
     * {@code NoApplicableTransitionException}, как предполагал открытый вопрос 1 тикета PHASE-05.
     * Guard'ы {@code IsValidDecisionType} и {@code HasCommentWhenRequired}, упомянутые в
     * {@code README.md} и в описании {@code DecideRequest}, в коде отсутствуют, а в
     * {@code transition_config} перехода {@code Decide} (V20) указан только {@code IsAssignedParticipant}:
     * значение {@code decisionType} не проверяется ни движком, ни actions и попадает в
     * {@code decision.result} как есть.
     *
     * @param processId    процесс, которому принадлежит этап
     * @param stageId      этап; участник ищется только в его последней итерации, поэтому решение по
     *                     предыдущей итерации после возврата на доработку невозможно
     * @param participantId участник, от имени которого принимается решение
     * @param decisionType тип решения ({@code APPROVE}, {@code APPROVE_WITH_COMMENTS}, {@code REJECT}
     *                     по спецификации); проверяется только требование комментария, само значение
     *                     записывается в {@code decision.result} без валидации
     * @param comment      комментарий; {@code null} означает «не указан» и не превращается в пустую
     *                     строку, иначе в {@code decision.comment} попадало {@code ""} и терялось
     *                     различие «комментарий не указан» / «указан пустой» (находка сборки)
     * @param actorId      действующий пользователь; должен совпадать с {@code participant.userId},
     *                     иначе guard не пропустит переход и вернётся {@code performed == false}
     * @return результат перехода; {@code performed == false} означает, что guards перехода
     *         {@code Decide} не прошли и решение не записано
     * @throws IllegalArgumentException если не найдены процесс, этап или участник, либо нарушено
     *                                  требование к комментарию
     * @throws IllegalStateException если у этапа нет ни одной итерации или участник не в статусе
     *                               {@code Assigned}
     */
    @Transactional
    public TransitionResult decide(
            UUID processId,
            UUID stageId,
            UUID participantId,
            String decisionType,
            String comment,
            UUID actorId) {

        // Валидация decisionType и comment
        validateDecision(decisionType, comment);

        // 1. Загрузить ProcessInstance
        ProcessInstance process = processRepository.findById(processId)
                .orElseThrow(() -> new IllegalArgumentException("Process not found: " + processId));

        // 2. Найти StageInstance
        StageInstance stage = process.getStages().stream()
                .filter(s -> s.getId().equals(stageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Stage not found: " + stageId));

        // 3. Найти Participant в текущей итерации
        StageIteration currentIteration = stage.getIterations().stream()
                .max(Comparator.comparing(StageIteration::getIterationIdx))
                .orElseThrow(() -> new IllegalStateException("No iterations found for stage: " + stageId));

        Participant participant = currentIteration.getParticipants().stream()
                .filter(p -> p.getId().equals(participantId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Participant not found: " + participantId));

        // Явное предусловие вместо молчаливого notPerformed: переход Decide объявлен только из
        // состояния Assigned, поэтому решение от участника в другом статусе раньше приводило к
        // NoApplicableTransitionException с неинформативным текстом либо к тихому отказу.
        if (!"Assigned".equals(participant.getStatus())) {
            throw new IllegalStateException(
                    "Participant %s is not assigned and cannot decide (status=%s)"
                            .formatted(participantId, participant.getStatus()));
        }

        // 4. Резолвить configId для PARTICIPANT state machine
        StateMachineConfig participantConfig = stateMachineConfigRepository
                .findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.PARTICIPANT,
                        process.getProcessType(),
                        process.getConfigVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "Participant state machine config not found for processType=%s, version=%d"
                                .formatted(process.getProcessType(), process.getConfigVersion())));

        // 5. Собрать TransitionContext с decisionType и comment в parameters.
        // Комментарий кладётся только при наличии: ранее null подменялся пустой строкой, из-за
        // чего в decision.comment попадало "" вместо NULL и терялось различие
        // «комментарий не указан» / «указан пустой» (находка сборки).
        Map<String, Object> parameters = comment == null
                ? Map.of("decisionType", decisionType)
                : Map.of("decisionType", decisionType, "comment", comment);

        TransitionContext context = new TransitionContext(
                participant,
                actorId,
                ActorType.USER,
                parameters);

        // 6. Вызвать TransitionEngine
        return transitionEngine.transition(
                EntityType.PARTICIPANT,
                participant.getId(),
                participantConfig.getId(),
                participant.getStatus(),
                TriggerType.USER_ACTION,
                context,
                participant::setStatus);
    }

    /**
     * Ранняя валидация намерения: без неё переход прошёл бы guards карты (они проверяют только
     * участника), а {@code RecordCommentAction} молча ничего не записал — решение с обязательным по
     * спецификации обоснованием сохранилось бы без обоснования.
     *
     * @throws IllegalArgumentException если {@code decisionType} требует комментария, а он пуст
     */
    private void validateDecision(String decisionType, String comment) {
        if ("APPROVE_WITH_COMMENTS".equals(decisionType) && (comment == null || comment.isBlank())) {
            throw new IllegalArgumentException("Comment is required for APPROVE_WITH_COMMENTS");
        }
        if ("REJECT".equals(decisionType) && (comment == null || comment.isBlank())) {
            throw new IllegalArgumentException("Comment is required for REJECT");
        }
    }
}
