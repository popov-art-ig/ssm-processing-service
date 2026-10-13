package ru.coordination.approval.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.coordination.approval.api.dto.process.*;
import ru.coordination.approval.api.exception.EntityNotFoundException;
import ru.coordination.approval.api.exception.ErrorResponse;
import ru.coordination.approval.api.service.ProcessViewService;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.service.ProcessService;

/**
 * Процессы согласования — {@code 07_api_contract.md} §12 (группа 3 «Процессы»), префикс
 * {@code /api/v1/processes}.
 *
 * <p>Запись делегируется {@link ProcessService} (переходы карты PROCESS через
 * {@code TransitionEngine}), чтение — {@link ProcessViewService}, который отдаёт агрегат
 * «процесс + этапы + итерации + участники» (ADR-020). Контроллер не маппит сущности сам:
 * read-only транзакция view-сервиса держит сессию открытой, пока маппер обходит ленивые
 * коллекции.
 *
 * <p>Эндпоинтов {@code submit}/{@code approve-route}/{@code reject-route} (§12.5–12.7, тип
 * UNIFIED) здесь нет: переходы {@code SubmitRoute}/{@code ApproveRoute}/{@code RejectRoute} не
 * зарегистрированы в картах — сценарий UNIFIED вынесен в отдельную фазу.
 */
@RestController
@RequestMapping("/api/v1/processes")
@RequiredArgsConstructor
@Tag(name = "Processes", description = "API для управления процессами согласования")
public class ProcessController {

    private final ProcessService processService;
    private final ProcessViewService processViewService;

    /**
     * Создаёт процесс из снимка сущности: {@link ProcessService#createProcess} подбирает шаблон по
     * правилам применимости и генерирует маршрут, после чего ответ собирается
     * {@link ProcessViewService#getById}.
     *
     * <p>Отвечает {@code 201 Created}. {@code 400} — тело не прошло {@code @Valid};
     * {@code 404} — ни один шаблон не подошёл ({@link EntityNotFoundException} из генератора
     * маршрута). Процесс создаётся в статусе {@code Draft}: маршрут сгенерирован, но не запущен.
     */
    @PostMapping
    @Operation(summary = "Создать процесс согласования",
               description = "Создает новый процесс на основе снимка сущности. Шаблон подбирается автоматически по правилам применимости.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Процесс успешно создан",
                     content = @Content(schema = @Schema(implementation = ProcessDto.class))),
        @ApiResponse(responseCode = "400", description = "Невалидные данные запроса",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Не найден подходящий шаблон",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProcessDto> createProcess(@Valid @RequestBody CreateProcessRequest request) {
        ProcessInstance created = processService.createProcess(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(processViewService.getById(created.getId()));
    }

    /**
     * Детальное представление процесса (ADR-020): этапы, итерации и участники собираются
     * {@link ProcessViewService#getById} внутри read-only транзакции.
     *
     * <p>Отвечает {@code 200}; {@code 404} — процесса нет ({@link EntityNotFoundException} из
     * {@link ProcessService#getById}).
     */
    @GetMapping("/{processId}")
    @Operation(summary = "Получить процесс по ID",
               description = "Возвращает детальную информацию о процессе, включая все этапы, итерации и участников")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Процесс найден",
                     content = @Content(schema = @Schema(implementation = ProcessDto.class))),
        @ApiResponse(responseCode = "404", description = "Процесс не найден",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProcessDto> getProcess(
            @Parameter(description = "ID процесса", required = true)
            @PathVariable UUID processId) {
        return ResponseEntity.ok(processViewService.getById(processId));
    }
    /**
     * Запускает процесс: {@link ProcessService#startProcess} выполняет переход
     * {@code Draft → InProgress} (actions активации первого этапа), после чего агрегат
     * перечитывается {@link ProcessViewService#getById}.
     *
     * <p>Отвечает {@code 200}; {@code 404} — процесса нет; {@code 409} — статус не {@code Draft}
     * ({@link IllegalStateException} из явного предусловия сервиса). Предусловие проверяется в
     * сервисе, а не здесь, потому что из {@code InProgress} переход по {@code USER_ACTION} тоже
     * существует ({@code ProcessRework}) и без проверки повторный запуск выглядел бы как успех.
     */
    @PostMapping("/{processId}/start")
    @Operation(summary = "Запустить процесс",
               description = "Переводит процесс из состояния Draft в InProgress")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Процесс успешно запущен",
                     content = @Content(schema = @Schema(implementation = ProcessDto.class))),
        @ApiResponse(responseCode = "404", description = "Процесс не найден",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Процесс уже запущен",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProcessDto> startProcess(
            @Parameter(description = "ID процесса", required = true)
            @PathVariable UUID processId,
            @Valid @RequestBody StartProcessRequest request) {
        processService.startProcess(processId, request.actorId());
        return ResponseEntity.ok(processViewService.getById(processId));
    }

    /**
     * Отзывает процесс: {@link ProcessService#recall} выполняет переход
     * {@code InProgress → Recalled}, а причина уходит в параметры перехода и попадает в
     * {@code audit_event.payload}.
     *
     * <p>Отвечает {@code 200} с перечитанным агрегатом; {@code 404} — процесса нет. Известные
     * ограничения: результат перехода не проверяется, поэтому при отказе guard'а
     * {@code IsRecallRequest} ответ всё равно {@code 200} с прежним статусом, а если для текущего
     * статуса подходящего перехода нет вообще, движок бросит
     * {@code NoApplicableTransitionException} и обработчик отдаст {@code 500}, хотя по смыслу это
     * конфликт состояния ({@code 409}).
     */
    @PostMapping("/{processId}/recall")
    @Operation(summary = "Отозвать процесс",
               description = "Переводит процесс из InProgress/OnRework в состояние Recalled")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Процесс успешно отозван",
                     content = @Content(schema = @Schema(implementation = ProcessDto.class))),
        @ApiResponse(responseCode = "404", description = "Процесс не найден",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProcessDto> recallProcess(
            @Parameter(description = "ID процесса", required = true)
            @PathVariable UUID processId,
            @Valid @RequestBody RecallProcessRequest request) {
        processService.recall(processId, request.actorId(), request.reason());
        return ResponseEntity.ok(processViewService.getById(processId));
    }

    /**
     * Возобновляет процесс после доработки: {@link ProcessService#resume} выполняет переход
     * {@code OnRework → InProgress} с активацией целевого этапа.
     *
     * <p>Контракт (§12.9) оперирует {@code targetStageId}, а переход {@code ResumeProcess}
     * получает порядковый индекс этапа, который читает guard {@code IsTargetStageAllowedGuard};
     * индекс восстанавливается здесь по списку этапов процесса. Поэтому неизвестный
     * {@code targetStageId} — это {@code 404} ({@link EntityNotFoundException}), а не {@code 400},
     * и ответ {@code 200} не означает, что этап активирован: отказ guard'а движок вернёт как
     * «переход не выполнен» и контроллер этого не заметит. {@code 409} — процесс не в статусе
     * {@code OnRework} ({@link IllegalStateException} из сервиса).
     */
    @PostMapping("/{processId}/resume")
    @Operation(summary = "Возобновить процесс после доработки",
               description = "Переводит процесс из OnRework обратно в InProgress с указанием целевого этапа")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Процесс успешно возобновлен",
                     content = @Content(schema = @Schema(implementation = ProcessDto.class))),
        @ApiResponse(responseCode = "404", description = "Процесс не найден",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProcessDto> resumeProcess(
            @Parameter(description = "ID процесса", required = true)
            @PathVariable UUID processId,
            @Valid @RequestBody ResumeProcessRequest request) {
        // Контракт API оперирует targetStageId (07_api_contract.md §12.9), а переход
        // ResumeProcess — порядковым индексом целевого этапа, который читает
        // IsTargetStageAllowedGuard из параметров. Восстанавливаем индекс по id этапа.
        int targetStageOrderIdx = processService.getById(processId).getStages().stream()
                .filter(stage -> stage.getId().equals(request.targetStageId()))
                .mapToInt(StageInstance::getOrderIdx)
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(
                        "Stage not found in process %s: %s".formatted(processId, request.targetStageId())));

        processService.resume(processId, targetStageOrderIdx, request.actorId());
        return ResponseEntity.ok(processViewService.getById(processId));
    }

    /**
     * Постраничный список процессов через {@link ProcessViewService}: фильтры уходят в
     * {@code Specification}, сортировка фиксирована — {@code createdAt DESC}, размер страницы
     * зажимается сверху.
     *
     * <p><b>Представление задаётся параметром {@code view}</b>:
     * <ul>
     *   <li>{@code full} (по умолчанию) — {@link ProcessDto} с этапами, итерациями и участниками:
     *       прежнее поведение сохраняется для клиентов, которым список нужен целиком;</li>
     *   <li>{@code summary} — {@link ProcessSummaryDto}: без этапов и участников, но с именем
     *       текущего этапа. Такой ответ стоит 1–2 запроса к БД на страницу вместо десятков
     *       ({@code doc/LOAD_TESTING.md} §5.3), и именно краткое представление описывает
     *       контракт §12.2.</li>
     * </ul>
     * Неизвестное значение {@code view} — это {@code 400} ({@code BAD_REQUEST}), а не молчаливый
     * откат к {@code full}: опечатка в параметре не должна выглядеть как другой объём ответа.
     *
     * <p>Отвечает {@code 200}. Пустые строковые фильтры трактуются как отсутствующие, поэтому
     * {@code ?status=} не сужает выборку. Ограничение размера страницы живёт в
     * {@link ProcessService#listProcesses} (значение больше допустимого молча урезается, а не
     * отвергается).
     */
    @GetMapping
    @Operation(summary = "Получить список процессов",
               description = "Возвращает постраничный список процессов с возможностью фильтрации. "
                       + "view=full (по умолчанию) — процессы с этапами и участниками; "
                       + "view=summary — краткие карточки без этапов и участников.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Список процессов",
                     content = @Content(schema = @Schema(implementation = Page.class))),
        @ApiResponse(responseCode = "400", description = "Неизвестное значение view",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Page<?>> listProcesses(
            @Parameter(description = "Фильтр по статусу")
            @RequestParam(required = false) String status,
            @Parameter(description = "Фильтр по инициатору")
            @RequestParam(required = false) UUID initiatorId,
            @Parameter(description = "Фильтр по типу сущности")
            @RequestParam(required = false) String entityType,
            @Parameter(description = "Представление: full (по умолчанию) или summary")
            @RequestParam(defaultValue = "full") String view,
            @Parameter(description = "Номер страницы (начиная с 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы")
            @RequestParam(defaultValue = "20") int size) {

        // Разбор значения без учёта регистра; неизвестное значение — IllegalArgumentException,
        // которую GlobalExceptionHandler отдаёт как 400 BAD_REQUEST.
        ProcessListView representation = ProcessListView.parse(view);

        // Обе ветки возвращают Page, отличаются только типом элемента: общий Page<?> сохраняет один
        // эндпоинт вместо второго метода с почти теми же аннотациями.
        Page<?> result = representation == ProcessListView.SUMMARY
                ? processViewService.listSummaries(status, initiatorId, entityType, page, size)
                : processViewService.list(status, initiatorId, entityType, page, size);
        return ResponseEntity.ok(result);
    }
}
