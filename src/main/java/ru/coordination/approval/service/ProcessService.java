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
 *
 * <p>Фазы: создание маршрута — PHASE-15, запуск — PHASE-03/04, отзыв и возобновление — PHASE-08,
 * чтение — PHASE-21-25. Переходы выполняются по той версии карт, которая записана в процессе
 * ({@code configVersion}) и по той версии шаблона, на которую указывает {@code templateRef}
 * (Snapshot-on-Start, ADR-003): изменение шаблона или публикация новой версии карты на уже
 * созданный процесс не влияют.
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
     *
     * <p>Процесс создаётся в статусе {@code Draft} ({@link RouteGenerationService#INITIAL_PROCESS_STATUS})
     * со ссылкой {@code templateRef} на конкретную версию шаблона: дальнейшие правки шаблона на
     * созданный маршрут не влияют (Snapshot-on-Start, ADR-003). Шаблон здесь не передаётся —
     * {@code templateId} намеренно {@code null}, то есть всегда работает автоподбор
     * ({@link MatchService#findMatchingTemplates}).
     *
     * @param request сущность-источник, её атрибуты и инициатор
     * @return сохранённый процесс со сгенерированным маршрутом
     * @throws ru.coordination.approval.exception.NoMatchingTemplateException если не подошёл ни один
     *                                   опубликованный шаблон
     * @throws ru.coordination.approval.exception.RequiredSlotNotResolvedException если обязательный
     *                                   слот не получил участника
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

    /**
     * Запускает процесс: {@code Draft -> InProgress} переходом {@code StartProcess} по триггеру
     * {@code USER_ACTION} (миграция V18, {@code 04_state_machines.md} §4.2). Actions перехода в той
     * же транзакции активируют первый этап (PHASE-04).
     *
     * <p><b>Предусловие по статусу — {@code Draft}, и оно проверяется явно.</b> Из {@code InProgress}
     * по {@code USER_ACTION} тоже существует переход ({@code RecallProcess}, миграция V23), поэтому
     * без явной проверки повторный запуск не упал бы, а вернул {@code notPerformed} — снаружи это
     * выглядело бы как успешный запуск (находка сборки). Проверка статуса надёжнее опоры на
     * отсутствие перехода: конфигурация переходов живёт в БД и может измениться без правки кода.
     *
     * <p>Право на запуск и корректность маршрута проверяют guards карты ({@code IsInitiator},
     * {@code AllMandatorySlotsFilled}, {@code AllDurationsValid}): {@code performed == false}
     * означает, что они не прошли.
     *
     * @param processId процесс в статусе {@code Draft}
     * @param actorId   пользователь, выполняющий запуск; должен быть инициатором процесса
     * @return результат перехода; {@code performed == false} — guards не прошли, статус не изменён
     * @throws EntityNotFoundException если процесса нет
     * @throws IllegalStateException если статус не {@code Draft} или нет карты переходов PROCESS
     *                               зафиксированной в процессе версии
     */
    @Transactional
    public TransitionResult startProcess(UUID processId, UUID actorId) {
        ProcessInstance process = requireProcess(processId);

        // Явная проверка вместо опоры на отсутствие перехода: из состояния InProgress переход
        // по USER_ACTION существует (RecallProcess, V23), поэтому TransitionEngine вернул бы
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
     * Отзыв процесса: {@code InProgress -> Recalled} и {@code OnRework -> Recalled} переходом
     * {@code RecallProcess} (миграция V23, {@code 07_api_contract.md} §12.8).
     *
     * <p>Причина отзыва передаётся в параметрах перехода и попадает в
     * {@code audit_event.payload} через {@link TransitionEngine}.
     *
     * <p>Предусловия по статусу здесь нет — в отличие от {@link #startProcess} и {@link #resume}.
     * {@code RecallProcess} объявлен только для {@code InProgress} и {@code OnRework}, но движок
     * выбирает исход из текущего состояния, поэтому вызов на черновике подходящего перехода не
     * находит не всегда: из {@code Draft} по {@code USER_ACTION} объявлен {@code StartProcess}, и
     * при совпадении {@code actorId} с инициатором он будет выполнен — отзыв черновика запустит
     * процесс. Это известный дефект: параметр {@code reason} guard'ами {@code StartProcess} не
     * проверяется.
     *
     * @param reason обоснование отзыва; {@code null} заменяется пустой строкой, но параметр остаётся
     *               в контексте — иначе guard {@code IsRecallRequest} не пропустит отзыв
     * @return результат перехода; {@code performed == false} — не прошёл {@code IsInitiator}
     * @throws EntityNotFoundException если процесса нет
     * @throws IllegalStateException если нет карты переходов PROCESS зафиксированной версии
     * @throws ru.coordination.approval.engine.exception.NoApplicableTransitionException если в текущем
     *                               статусе нет ни одного перехода по {@code USER_ACTION}
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

    /**
     * Возобновляет процесс после доработки: {@code OnRework -> InProgress} переходом
     * {@code ResumeProcess} (миграция V23, PHASE-08; {@code 07_api_contract.md} §12.9).
     *
     * <p><b>Предусловие по статусу — {@code OnRework}, и оно проверяется явно</b> по той же причине,
     * что в {@link #startProcess}: из {@code InProgress} по {@code USER_ACTION} есть переход
     * {@code RecallProcess}, поэтому вызов на неверном статусе не упал бы, а вернул
     * {@code notPerformed} — снаружи это неотличимо от «guards не пропустили».
     *
     * <p>Actions перехода: {@code RejectStagesFrom} помечает {@code Rejected} этапы между целевым и
     * текущим, {@code ActivateTargetStage} создаёт новую итерацию целевого этапа — то есть смена
     * статуса процесса сопровождается перестройкой маршрута в той же транзакции. Условия проверяют
     * guards {@code IsInitiator}, {@code AllRemarksProcessed} (до V24 — заглушка, затем реальная
     * проверка необработанных замечаний) и {@code IsTargetStageAllowed} (упрощённая реализация).
     *
     * @param targetStageOrderIdx {@code orderIdx} этапа, на который возвращается процесс; передаётся
     *                            в параметрах перехода и проверяется guard'ом целевого этапа
     * @param actorId             пользователь, выполняющий возобновление; должен быть инициатором
     * @return результат перехода; {@code performed == false} — не прошли guards, статус не изменён
     * @throws EntityNotFoundException если процесса нет
     * @throws IllegalStateException если статус не {@code OnRework} или нет карты переходов PROCESS
     *                               зафиксированной версии
     */
    @Transactional
    public TransitionResult resume(UUID processId, Integer targetStageOrderIdx, UUID actorId) {
        ProcessInstance process = requireProcess(processId);

        // Явное предусловие вместо опоры на отсутствие перехода: ResumeProcess объявлен только из
        // OnRework, и без этой проверки попытка возобновить процесс в другом статусе выглядела бы
        // как молчаливый notPerformed (из InProgress переход по USER_ACTION существует — RecallProcess, V23).
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

    /**
     * Читает процесс вместе со связями (этапы, итерации, участники) для представления в API.
     *
     * @param processId идентификатор процесса
     * @return сущность процесса; {@code null} не возвращается
     * @throws EntityNotFoundException если процесса нет — контроллер превращает это в 404
     */
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
     *
     * <p><b>Размер страницы ограничен.</b> {@code size <= 0} заменяется на 20 (умолчание
     * {@code 07_api_contract.md} §8.1), значение больше {@link #MAX_PAGE_SIZE} срезается до него;
     * без этого {@code GET /processes?size=1000000} тянул всю таблицу в память (находка аудита).
     * Отрицательный {@code page} приводится к 0; сортировка всегда {@code createdAt} по убыванию и
     * параметром запроса не управляется, хотя §8.3 предполагает произвольный {@code sort}.
     *
     * <p>Незаполненный фильтр превращается в {@code Specification.unrestricted()}, а не в
     * {@code null} — см. {@link #equalsIfPresent}.
     *
     * @param status      точное равенство статусу; {@code null} или пустая строка — без фильтра
     * @param initiatorId точное равенство инициатору; {@code null} — без фильтра
     * @param entityType  точное равенство типу сущности; {@code null} или пустая строка — без фильтра
     * @param page        номер страницы, отрицательный приводится к 0
     * @param size        размер страницы; вне диапазона {@code 1..MAX_PAGE_SIZE} приводится к границе
     * @return страница процессов; пустая, если ничего не найдено
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

    /** @throws EntityNotFoundException если процесса с таким {@code processId} нет */
    private ProcessInstance requireProcess(UUID processId) {
        return processRepository.findById(processId)
                .orElseThrow(() -> new EntityNotFoundException("Process not found: " + processId));
    }

    /**
     * Берёт карту переходов строго по версии, зафиксированной в процессе
     * ({@code process.getConfigVersion()}), а не по последней опубликованной: иначе уже идущий
     * процесс перешёл бы по новым правилам (Snapshot-on-Start, ADR-003).
     *
     * @throws IllegalStateException если карты для пары «тип процесса + версия» нет — это признак
     *                               повреждённой конфигурации, а не ошибка пользователя
     */
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

    /**
     * Приводит запрошенный размер страницы к допустимому: {@code size <= 0} — умолчание 20, больше
     * {@link #MAX_PAGE_SIZE} — сам предел. Верхняя граница защищает от выгрузки всей таблицы в
     * память по запросу клиента.
     */
    private static int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * Строит предикат равенства, если фильтр задан, и нейтральный {@code Specification.unrestricted()},
     * если значение отсутствует или является пустой строкой. Причина — в комментарии ниже: для
     * {@code Specification.allOf} нейтральный элемент обязателен, а {@code null} недопустим.
     */
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
