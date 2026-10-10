package ru.coordination.approval.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.process.AuthorRole;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.ProcessRepository;
import ru.coordination.approval.domain.process.Remark;
import ru.coordination.approval.domain.process.RemarkRepository;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.domain.statemachine.TriggerType;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.TransitionEngine;
import ru.coordination.approval.engine.TransitionResult;
import ru.coordination.approval.engine.model.StateMachineConfigRepository;

/**
 * Сервис управления замечаниями (PHASE-11).
 * Предоставляет методы для создания, обработки и отклонения замечаний.
 *
 * <p>Работает с сущностью {@code Remark} агрегата процесса через {@code RemarkStateMachine}
 * (миграция V24). Реализованный жизненный цикл: {@code Draft --CreateRemark--> Active} при
 * создании и {@code Active --StartProcessingRemark--> InProgress} с последующим
 * {@code ProcessRemark} (в {@code Processed}) либо {@code RejectRemark} (в {@code Rejected}).
 *
 * <p><b>Почему финальный исход выбирает сервис, а не карта переходов.</b> Из {@code InProgress}
 * доступны два перехода, и оба guard'а ({@code IsRemarkResolution} и {@code IsRemarkRejection},
 * миграция V24) различают их только по наличию параметра перехода, а не по намерению вызывающего
 * кода. Движок в такой ситуации выбирает переход по {@code priority}, затем по коду, и
 * {@code ProcessRemark} лексикографически меньше {@code RejectRemark}: вызов отклонения уходил бы в
 * обработку. Поэтому {@link #processRemark} передаёт параметр {@code resolution}, а
 * {@link #rejectRemark} — {@code reason}, и нужный исход выбирается по данным, а не по порядку
 * сортировки (обоснование зафиксировано в комментарии миграции V24).
 *
 * <p><b>Известные расхождения.</b> Состав статусов отличается от {@code 04_state_machines.md}
 * §10.1–10.2: спецификация описывает {@code Proposed/Resolved/Closed/AutoProcessed}, а миграция и
 * сервис используют {@code Draft/Active/InProgress/Processed/Rejected}; переходы
 * {@code MarkNotRequired}, активация замечания доп. согласующего и закрытие замечания при закрытии
 * этапа ({@code AutoProcessOpenRemarks}, критерий приёмки 8 PHASE-11) в сервисе отсутствуют.
 * Guard {@code IsParticipant} перехода {@code CreateRemark} — заглушка, всегда {@code true}.
 *
 * <p><b>Сервисный слой не достижим по HTTP.</b> Все эндпоинты замечаний в
 * {@code RemarkController} (создание, resolve, reject, accept, reject-resolution) бросают
 * {@code UnsupportedOperationException} — привязка контроллеров не сделана (см. {@code README.md},
 * «Не реализовано в REST API»).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RemarkService {

    /**
     * Состояние карты переходов, из которого создаётся замечание. Совпадает с {@code from_state}
     * перехода {@code CreateRemark} и {@code is_initial = true} для REMARK (миграция
     * {@code V24__seed_remark_lifecycle_transitions.sql}). Итоговый статус созданного замечания —
     * {@link #CREATE_REMARK_TO_STATE}.
     */
    static final String CREATE_REMARK_FROM_STATE = "Draft";

    /** Статус созданного замечания ({@code to_state} перехода {@code CreateRemark}). */
    static final String CREATE_REMARK_TO_STATE = "Active";

    private final RemarkRepository remarkRepository;
    private final ProcessRepository processRepository;
    private final StateMachineConfigRepository stateMachineConfigRepository;
    private final TransitionEngine transitionEngine;

    /**
     * Создаёт замечание к этапу и сразу активирует его: {@code Draft -> Active} переходом
     * {@code CreateRemark}. Промежуточного «предложенного» состояния нет — замечание основного
     * согласующего действует с момента создания (критерий приёмки 1 PHASE-11).
     *
     * <p>Статус {@code Draft} в {@code state_config} нужен только как {@code is_initial} карты:
     * замечание никогда не сохраняется в нём. Если движок не выполнил переход, замечание не
     * сохраняется — вызывающий получает {@link IllegalStateException}, а не «полузамечание».
     *
     * @param processId процесс, к которому относится замечание
     * @param stageId   этап процесса; замечание всегда привязано к этапу
     * @param text      текст замечания
     * @param actorId   автор; попадает в {@code authorId} и в контекст перехода (guard
     *                  {@code IsParticipant} — заглушка, реальная проверка участия не выполняется)
     * @return сохранённое замечание в статусе {@link #CREATE_REMARK_TO_STATE}
     * @throws IllegalArgumentException если процесс или этап не найдены
     * @throws IllegalStateException если нет карты переходов REMARK или переход {@code CreateRemark}
     *                               не прошёл guards
     */
    @Transactional
    public Remark createRemark(
            UUID processId,
            UUID stageId,
            String text,
            UUID actorId) {

        ProcessInstance process = processRepository.findById(processId)
                .orElseThrow(() -> new IllegalArgumentException("Process not found"));

        StageInstance stage = process.getStages().stream()
                .filter(s -> s.getId().equals(stageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Stage not found"));

        StateMachineConfig config = stateMachineConfigRepository
                .findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.REMARK,
                        process.getProcessType(),
                        process.getConfigVersion())
                .orElseThrow(() -> new IllegalStateException("Remark state machine config not found"));

        Remark remark = Remark.builder()
                .id(UUID.randomUUID())
                .process(process)
                .stage(stage)
                .authorId(actorId)
                // author_role объявлен NOT NULL в схеме (V8__remarks_and_comments.sql, строка 10),
                // но не заполнялся — создание замечания падало с DataIntegrityViolationException
                // на уровне БД (находка сборки). Автор — основной согласующий этапа.
                .authorRole(AuthorRole.APPROVER)
                .text(text)
                .status(CREATE_REMARK_TO_STATE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Map<String, Object> parameters = Map.of();
        TransitionContext context = new TransitionContext(
                remark, actorId, ActorType.USER, parameters);

        TransitionResult result = transitionEngine.transition(
                EntityType.REMARK,
                remark.getId(),
                config.getId(),
                CREATE_REMARK_FROM_STATE,
                TriggerType.USER_ACTION,
                context,
                remark::setStatus);

        if (!result.performed()) {
            throw new IllegalStateException(
                    "Failed to create remark %s: transition %s -> %s not performed"
                            .formatted(remark.getId(), CREATE_REMARK_FROM_STATE, CREATE_REMARK_TO_STATE));
        }

        return remarkRepository.save(remark);
    }

    /**
     * Помечает замечание исправленным: доводит его до {@code InProgress} (если оно ещё
     * {@code Active}) и выполняет переход {@code ProcessRemark} в {@code Processed}. Параметр
     * {@code resolution} — признак для guard {@code IsRemarkResolution}, отличающий обработку от
     * отклонения (см. описание класса).
     *
     * @param remarkId   замечание; допускается как {@code Active}, так и уже взятое в работу
     *                   {@code InProgress} — повторный {@code StartProcessingRemark} не выполняется
     * @param resolution описание исправления
     * @param actorId    действующий пользователь; должен быть ответственным по замечанию
     *                   (guard {@code IsRemarkAssignee}), иначе переход не выполнится
     * @return результат перехода; {@code performed == false} — guards не прошли, статус не изменён
     * @throws IllegalArgumentException если замечание не найдено
     * @throws IllegalStateException если карта переходов REMARK недоступна
     * @throws ru.coordination.approval.engine.exception.NoApplicableTransitionException если из
     *                               текущего статуса нет перехода (например, замечание уже
     *                               {@code Processed} или {@code Rejected})
     */
    @Transactional
    public TransitionResult processRemark(UUID remarkId, String resolution, UUID actorId) {
        // Жизненный цикл: Active --StartProcessingRemark--> InProgress --ProcessRemark--> Processed.
        // Промежуточный InProgress фиксируется в audit_event; состояние между двумя переходами
        // извне не наблюдаемо, поэтому сервис выполняет оба шага за один вызов.
        Remark remark = takeIntoWork(remarkId, actorId);

        return transitionEngine.transition(
                EntityType.REMARK,
                remark.getId(),
                requireConfig(remark).getId(),
                remark.getStatus(),
                TriggerType.USER_ACTION,
                new TransitionContext(remark, actorId, ActorType.USER,
                        Map.of("resolution", resolution)),
                remark::setStatus);
    }

    /**
     * Отклоняет замечание: как и {@link #processRemark}, сначала доводит замечание до
     * {@code InProgress}, затем выполняет {@code RejectRemark} в {@code Rejected}. Параметр
     * {@code reason} — признак для guard {@code IsRemarkRejection}; без него из {@code InProgress}
     * был бы выбран {@code ProcessRemark} (см. описание класса).
     *
     * @param remarkId замечание в статусе {@code Active} или {@code InProgress}
     * @param reason   обоснование отклонения
     * @param actorId  действующий пользователь; проверяется guard {@code IsRemarkAssignee}
     * @return результат перехода; {@code performed == false} — guards не прошли
     * @throws IllegalArgumentException если замечание не найдено
     * @throws IllegalStateException если карта переходов REMARK недоступна
     * @throws ru.coordination.approval.engine.exception.NoApplicableTransitionException если из
     *                               текущего статуса нет подходящего перехода
     */
    @Transactional
    public TransitionResult rejectRemark(UUID remarkId, String reason, UUID actorId) {
        // Жизненный цикл: Active --StartProcessingRemark--> InProgress --RejectRemark--> Rejected.
        Remark remark = takeIntoWork(remarkId, actorId);

        return transitionEngine.transition(
                EntityType.REMARK,
                remark.getId(),
                requireConfig(remark).getId(),
                remark.getStatus(),
                TriggerType.USER_ACTION,
                new TransitionContext(remark, actorId, ActorType.USER,
                        Map.of("reason", reason)),
                remark::setStatus);
    }

    /**
     * A-R-007 TakeIntoWork: Active -> InProgress. Возвращает замечание с АКТУАЛЬНЫМ статусом.
     *
     * <p>Если замечание уже взято в работу (InProgress) — шаг пропускается. Возврат значения из
     * {@link RemarkRepository#save} принципиален: следующему вызову движка нужно передать новый
     * статус, иначе он ищет переход из устаревшего состояния и не находит его
     * ({@code NoApplicableTransitionException}).
     */
    private Remark takeIntoWork(UUID remarkId, UUID actorId) {
        Remark remark = requireRemark(remarkId);

        if (!"Active".equals(remark.getStatus())) {
            return remark;
        }

        transitionEngine.transition(
                EntityType.REMARK,
                remark.getId(),
                requireConfig(remark).getId(),
                remark.getStatus(),
                TriggerType.USER_ACTION,
                new TransitionContext(remark, actorId, ActorType.USER, Map.of()),
                remark::setStatus);

        return remarkRepository.save(remark);
    }

    /** @throws IllegalArgumentException если замечания с таким {@code remarkId} нет */
    private Remark requireRemark(UUID remarkId) {
        return remarkRepository.findById(remarkId)
                .orElseThrow(() -> new IllegalArgumentException("Remark not found: " + remarkId));
    }

    /**
     * Берёт карту переходов по типу процесса и версии, зафиксированным в процессе замечания:
     * версия не перечитывается заново, иначе замечание старого процесса перешло бы по новой карте
     * (Snapshot-on-Start, ADR-003).
     *
     * @throws IllegalStateException если карты для этой пары «тип процесса + версия» нет
     */
    private StateMachineConfig requireConfig(Remark remark) {
        return stateMachineConfigRepository
                .findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.REMARK,
                        remark.getProcess().getProcessType(),
                        remark.getProcess().getConfigVersion())
                .orElseThrow(() -> new IllegalStateException("Remark config not found"));
    }
}
