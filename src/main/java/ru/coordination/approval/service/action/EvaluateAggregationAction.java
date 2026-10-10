package ru.coordination.approval.service.action;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageRepository;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.domain.statemachine.TriggerType;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.TransitionEngine;
import ru.coordination.approval.engine.TransitionResult;
import ru.coordination.approval.engine.model.StateMachineConfigRepository;
import ru.coordination.approval.engine.registry.Action;

/**
 * «Оценить агрегацию решений» — проверяет все решения участников и программно запускает один из
 * трёх переходов этапа (PHASE-06; модель агрегации — {@code 05_guards_actions_registry.md} §4.2,
 * действия этапа — §5.2). Резолвится {@code action_registry.handler = 'evaluateAggregationAction'};
 * зарегистрирован в миграции V20, а не в V21 (см. ниже). Включён в actions перехода {@code Decide}
 * ({@code Assigned → Decided}, уровень {@code PARTICIPANT}) последним, после
 * {@link RecordDecisionAction} и {@link RecordCommentAction}.
 *
 * <p><b>Что здесь от спецификации, а что нет.</b> §4.2 описывает общую функцию
 * {@code evaluateAggregation(stage)} и три guard'а-предиката над ней, но отдельного действия
 * «выполнить агрегацию» в §5.2 нет: там {@code A-S-004} — это {@code CloseIteration}, а
 * {@code A-S-005} — {@code AutoApproveMissing}. Приписанный реестром V20 код {@code A-S-004}
 * поэтому не соответствует спецификации, и ссылаться на него как на определение нельзя.
 *
 * <p><b>Какое состояние меняет.</b> Статус {@link StageInstance} — вложенным переходом того же
 * движка ({@code STAGE}/{@code SYSTEM_ACTION}) в один из {@code StageApproved},
 * {@code StageApprovedWithComments} или {@code StageOnRework} в зависимости от режима агрегации
 * ({@code decisionMode}) и от того, какой guard из тройки
 * {@code AggregationApproved}/{@code AggregationApprovedWithComments}/{@code AggregationRework}
 * прошёл. Состояние меняется только переходом, а не присваиванием {@code stage.status}
 * (ADR-002/ADR-028).
 *
 * <p><b>Почему регистрация в V20, а не в V21.</b> Действие применяется в агрегации решений,
 * которую описывает V21, однако его {@code action_registry} и переход-владелец {@code Decide}
 * живут в V20. Миграции применяются по возрастанию номера, поэтому регистрация в V21 означала бы,
 * что регистрация строки реестра выполнилась бы уже после {@code UPDATE transition_config},
 * добавляющего действие в actions перехода. Это отмечено комментарием в V20 и воспроизводит
 * исходный дефект «этап никогда не закрывался».
 *
 * <p><b>Почему {@code performed == false} — норма.</b> Действие вызывается после <i>каждого</i>
 * решения участника, а закрывать этап можно только когда решили все. Отказ виден только в
 * debug-логе; ошибкой он не является (в отличие от {@link ActivateNextStageAction}, где отказ
 * означает зависший процесс).
 *
 * <p>Вложенный вызов {@link TransitionEngine#transition} выполняется в той же транзакции, что и
 * внешний переход {@code Decide}. Если ни один переход этапа не выполнен
 * ({@link TransitionResult#performed()} {@code == false}, guards не прошли) — это не ошибка:
 * агрегация вызывается после каждого решения участника, но срабатывает только когда все участники
 * приняли решение (guard {@code AllParticipantsDecided}).
 */
@Component("evaluateAggregationAction")
@Slf4j
@RequiredArgsConstructor
public class EvaluateAggregationAction implements Action {

    private final StateMachineConfigRepository stateMachineConfigRepository;
    private final TransitionEngine transitionEngine;
    private final StageRepository stageRepository;

    /**
     * Пытается закрыть этап участника перехода.
     *
     * @param context контекст перехода {@code Decide}; сущность — {@link Participant}, этап
     *                берётся через {@code participant.getStageIteration().getStage()}
     * @throws IllegalStateException если нет {@code STAGE}-конфига для типа процесса и версии
     *         процесса (ошибка конфигурации)
     */
    @Override
    public void execute(TransitionContext context) {
        Participant participant = (Participant) context.entity();
        StageInstance stage = participant.getStageIteration().getStage();

        // Статус этапа читается из репозитория, а не из навигации по участнику: экземпляр
        // StageInstance, полученный через participant -> stageIteration -> stage, может быть
        // устаревшим (в кэше сессии он остался в статусе до активации — например Pending).
        // Из-за этого переход выполнялся «из Pending», кандидатов не находилось, и агрегация
        // молча не срабатывала: этап никогда не закрывался.
        String currentStageStatus = stageRepository.findById(stage.getId())
                .map(StageInstance::getStatus)
                .orElse(stage.getStatus());

        StateMachineConfig stageConfig = stateMachineConfigRepository
                .findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.STAGE,
                        stage.getProcess().getProcessType(),
                        stage.getProcess().getConfigVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "No StateMachineConfig for entityType=STAGE, processType=%s, version=%d"
                                .formatted(
                                        stage.getProcess().getProcessType(),
                                        stage.getProcess().getConfigVersion())));

        TransitionContext stageContext =
                new TransitionContext(stage, context.actorId(), ActorType.SYSTEM, Map.of());

        TransitionResult result = transitionEngine.transition(
                EntityType.STAGE,
                stage.getId(),
                stageConfig.getId(),
                currentStageStatus,
                TriggerType.SYSTEM_ACTION,
                stageContext,
                stage::setStatus);

        // Если ни один переход не выполнен (AllParticipantsDecided ещё false) — это нормально,
        // агрегация вызывается после каждого решения, но срабатывает только когда все решили
        if (!result.performed()) {
            log.debug("aggregation transition not performed for stage {} (status={})",
                    stage.getId(), currentStageStatus);
        }
    }
}
