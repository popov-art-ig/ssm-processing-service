package ru.coordination.approval.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.coordination.approval.api.dto.process.CreateProcessRequest;
import ru.coordination.approval.api.exception.EntityNotFoundException;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.ProcessRepository;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.domain.statemachine.TriggerType;
import ru.coordination.approval.dto.generation.EntitySnapshot;
import ru.coordination.approval.dto.generation.RouteGenerationRequest;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.TransitionEngine;
import ru.coordination.approval.engine.TransitionResult;
import ru.coordination.approval.engine.model.StateMachineConfigRepository;

/**
 * Use-case сервис процессов согласования.
 *
 * <p>Объединяет два слоя, которые ранее были разорваны (см. аудит коммита
 * {@code 08be95d "feat: implement REST API for UI (PHASE-21-25)"} — REST-контроллеры и их
 * тесты вызывали методы, которых в сервисном слое не существовало):
 * <ul>
 *   <li>создание процесса из снимка сущности — {@link #createProcess};</li>
 *   <li>переходы жизненного цикла через {@link TransitionEngine} — {@link #startProcess},
 *       {@link #recall}, {@link #resume};</li>
 *   <li>чтение — {@link #getById}, {@link #listProcesses}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ProcessService {

    /**
     * Верхняя граница размера страницы. Без неё {@code GET /processes?size=1000000} тянет всю
     * таблицу в память (находка аудита: неограниченный {@code size}).
     */
    static final int MAX_PAGE_SIZE = 100;

    private final ProcessRepository processRepository;
    private final StateMachineConfigRepository stateMachineConfigRepository;
    private final TransitionEngine transitionEngine;
    private final RouteGenerationService routeGenerationService;

    // ------------------------------------------------------------------ запись

    /**
     * Создаёт процесс по снимку сущности: подбирает шаблон по правилам применимости
     * (или берёт явно указанный) и генерирует маршрут.
     *
     * <p>Атрибуты и подтип сущности берутся из самого запроса, а не из {@code EntityAdapter}:
     * иначе {@code EntityAdapterStub} подменил бы их фиксированными значениями и подбор шаблона
     * игнорировал бы данные клиента.
     */
    @Transactional
    public ProcessInstance createProcess(CreateProcessRequest request) {
        EntitySnapshot snapshot = new EntitySnapshot(
                request.entityType(),
                request.entitySubtype(),
                request.entityId(),
                request.attributes() != null ? request.attributes() : Map.of(),
                Instant.now());

        RouteGenerationRequest routeRequest = new RouteGenerationRequest(
                request.entityType(),
                request.entityId(),
                null, // templateId: null -> автоподбор через MatchService
                null, // organizationId
                request.initiatorId());

        return routeGenerationService.generateRoute(routeRequest, snapshot);
    }

    @Transactional
    public TransitionResult startProcess(UUID processId, UUID actorId) {
        ProcessInstance process = requireProcess(processId);

        // Явная проверка вместо опоры на отсутствие перехода: из состояния InProgress переход
        // по USER_ACTION существует (ProcessRework), поэтому TransitionEngine вернул бы
        // notPerformed молча, и повторный запуск выглядел бы как успех (находка сборки).
        if (!RouteGenerationService.INITIAL_PROCESS_STATUS.equals(process.getStatus())) {
            throw new IllegalStateException(
                    "Process %s cannot be started: expected status %s but was %s"
                            .formatted(processId, RouteGenerationService.INITIAL_PROCESS_STATUS, process.getStatus()));
        }

        return transitionEngine.transition(
                EntityType.PROCESS,
                process.getId(),
                requireProcessConfig(process).getId(),
                process.getStatus(),
                TriggerType.USER_ACTION,
                new TransitionContext(process, actorId, ActorType.USER, Map.of()),
                process::setStatus);
    }

    /**
     * Отзыв процесса (InProgress/OnRework -> Recalled).
     *
     * <p>Причина отзыва передаётся в параметрах перехода и попадает в
     * {@code audit_event.payload} через {@link TransitionEngine}.
     */
    @Transactional
    public TransitionResult recall(UUID processId, UUID actorId, String reason) {
        ProcessInstance process = requireProcess(processId);

        // reason обязателен: guard IsRecallRequest отличает отзыв от возобновления по наличию
        // этого параметра (из OnRework доступны оба перехода).
        Map<String, Object> parameters =
                Map.of("reason", reason == null ? "" : reason);

        return transitionEngine.transition(
                EntityType.PROCESS,
                process.getId(),
                requireProcessConfig(process).getId(),
                process.getStatus(),
                TriggerType.USER_ACTION,
                new TransitionContext(process, actorId, ActorType.USER, parameters),
                process::setStatus);
    }

    @Transactional
    public TransitionResult resume(UUID processId, Integer targetStageOrderIdx, UUID actorId) {
        ProcessInstance process = requireProcess(processId);

        // Явное предусловие вместо опоры на отсутствие перехода: ResumeProcess объявлен только из
        // OnRework, и без этой проверки попытка возобновить процесс в другом статусе выглядела бы
        // как молчаливый notPerformed (из InProgress переход по USER_ACTION существует — ProcessRework).
        if (!"OnRework".equals(process.getStatus())) {
            throw new IllegalStateException("Process is not OnRework");
        }

        Map<String, Object> parameters = Map.of("targetStageOrderIdx", targetStageOrderIdx);

        return transitionEngine.transition(
                EntityType.PROCESS,
                process.getId(),
                requireProcessConfig(process).getId(),
                process.getStatus(),
                TriggerType.USER_ACTION,
                new TransitionContext(process, actorId, ActorType.USER, parameters),
                process::setStatus);
    }

    // ------------------------------------------------------------------ чтение

    @Transactional(readOnly = true)
    public ProcessInstance getById(UUID processId) {
        return requireProcess(processId);
    }

    /**
     * Постраничный список процессов с необязательными фильтрами.
     *
     * <p>Ранее контроллер принимал {@code status}/{@code initiatorId}/{@code entityType},
     * но игнорировал их и вызывал {@code findAll(pageable)} — фильтры не работали, при этом
     * соответствующий тест назывался {@code listProcesses_withFilters_success}.
     */
    @Transactional(readOnly = true)
    public Page<ProcessInstance> listProcesses(
            String status, UUID initiatorId, String entityType, int page, int size) {

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<ProcessInstance> spec = Specification.allOf(
                equalsIfPresent("status", status),
                equalsIfPresent("initiatorId", initiatorId),
                equalsIfPresent("entityType", entityType));

        return processRepository.findAll(spec, pageable);
    }

    // ------------------------------------------------------------------ внутреннее

    private ProcessInstance requireProcess(UUID processId) {
        return processRepository.findById(processId)
                .orElseThrow(() -> new EntityNotFoundException("Process not found: " + processId));
    }

    private StateMachineConfig requireProcessConfig(ProcessInstance process) {
        return stateMachineConfigRepository
                .findByEntityTypeAndProcessTypeAndVersion(
                        EntityType.PROCESS,
                        process.getProcessType(),
                        process.getConfigVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "No StateMachineConfig for entityType=PROCESS, processType=%s, version=%d"
                                .formatted(process.getProcessType(), process.getConfigVersion())));
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static <T> Specification<ProcessInstance> equalsIfPresent(String field, T value) {
        // Отсутствующий фильтр — это НЕ null: Specification.allOf запрещает null-элементы и
        // падает с «Other specification must not be null». Из-за этого GET /api/v1/processes
        // без параметров отвечал 400, хотя фильтры необязательны. Нейтральный элемент —
        // unrestricted().
        if (value == null) {
            return Specification.unrestricted();
        }
        if (value instanceof String s && s.isBlank()) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get(field), value);
    }
}
