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
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RemarkService {

    /**
     * Состояние карты переходов, из которого создаётся замечание. Совпадает с {@code from_state}
     * перехода {@code CreateRemark} и {@code is_initial = true} для REMARK (миграция
     * {@code V32__fix_remark_lifecycle_transitions.sql}). Итоговый статус созданного замечания —
     * {@link #CREATE_REMARK_TO_STATE}.
     */
    static final String CREATE_REMARK_FROM_STATE = "Draft";

    /** Статус созданного замечания ({@code to_state} перехода {@code CreateRemark}). */
    static final String CREATE_REMARK_TO_STATE = "Active";

    private final RemarkRepository remarkRepository;
    private final ProcessRepository processRepository;
    private final StateMachineConfigRepository stateMachineConfigRepository;
    private final TransitionEngine transitionEngine;

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

    private Remark requireRemark(UUID remarkId) {
        return remarkRepository.findById(remarkId)
                .orElseThrow(() -> new IllegalArgumentException("Remark not found: " + remarkId));
    }

    private StateMachineConfig requireConfig(Remark remark) {
        return stateMachineConfigRepository
                .findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.REMARK,
                        remark.getProcess().getProcessType(),
                        remark.getProcess().getConfigVersion())
                .orElseThrow(() -> new IllegalStateException("Remark config not found"));
    }
}
