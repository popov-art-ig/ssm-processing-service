package ru.coordination.approval.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.coordination.approval.api.dto.template.*;
import ru.coordination.approval.api.exception.EntityNotFoundException;
import ru.coordination.approval.api.exception.ErrorResponse;
import ru.coordination.approval.api.mapper.TemplateMapper;
import ru.coordination.approval.api.mapper.TemplateRequestMapper;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;
import ru.coordination.approval.engine.TransitionResult;
import ru.coordination.approval.service.TemplateService;

/**
 * Управление шаблонами маршрутов ({@code 07_api_contract.md} §10, PHASE-14).
 *
 * <p>Жизненный цикл — через {@link TemplateService} и карту переходов TEMPLATE
 * (`04_state_machines.md`, миграция V25): {@code PublishTemplate} DRAFT→PUBLISHED,
 * {@code DeprecateTemplate} PUBLISHED→DEPRECATED, {@code ArchiveTemplate} DEPRECATED→ARCHIVED.
 * Гварды и действия карты ({@code AllMandatorySlotsValid}, {@code ValidateTemplateStructure},
 * {@code SetPublishedTimestamp}, {@code NoActiveProcesses}, {@code SetArchivedTimestamp}) уже
 * зарегистрированы в реестрах, поэтому валидация структуры выполняется движком, а не
 * дублируется в контроллере.
 *
 * <p>Версионирование (ADR-023): {@code PUT} по опубликованному шаблону создаёт **новую строку**
 * {@code Template} с {@code version + 1} и {@code parentTemplateId}, поэтому в ответе может
 * вернуться шаблон с другим {@code id} — это не ошибка.
 *
 * <p>Все методы чтения помечены {@code @Transactional(readOnly = true)}: маппинг обходит ленивые
 * коллекции ({@code stages -> actorSlots}, {@code applicabilityRules}) при
 * {@code spring.jpa.open-in-view=false}.
 */
@RestController
@RequestMapping("/api/v1/admin/templates")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Templates", description = "API для управления шаблонами процессов согласования")
public class TemplateController {

    private final TemplateRepository templateRepository;
    private final TemplateMapper templateMapper;
    private final TemplateRequestMapper templateRequestMapper;
    private final TemplateService templateService;

    /**
     * Создаёт шаблон в статусе {@code DRAFT} через {@link TemplateService#create}; тело запроса
     * предварительно переводится в DTO сервисного слоя {@link TemplateRequestMapper} (в API
     * перечисления приходят строками).
     *
     * <p>Отвечает {@code 201} с заголовком {@code Location} на созданный ресурс. {@code 400} —
     * тело не прошло {@code @Valid} или неизвестное значение {@code processType}
     * ({@link IllegalArgumentException} из маппера).
     */
    @PostMapping
    @Operation(summary = "Создать шаблон",
               description = "Создает новый шаблон процесса согласования в статусе DRAFT")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Шаблон создан",
                     content = @Content(schema = @Schema(implementation = TemplateDto.class))),
        @ApiResponse(responseCode = "400", description = "Невалидные данные",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @Transactional
    public ResponseEntity<TemplateDto> createTemplate(@Valid @RequestBody CreateTemplateRequest request) {
        Template created = templateService.create(templateRequestMapper.toServiceRequest(request));

        return ResponseEntity.created(URI.create("/api/v1/admin/templates/" + created.getId()))
                .body(templateMapper.toDto(created));
    }

    /**
     * Обновляет шаблон: {@code DRAFT} правится на месте, {@code PUBLISHED} — через
     * {@link TemplateService#update} создаёт <b>новую версию</b> (ADR-023), поэтому ответ может
     * содержать другой {@code id}, чем в пути.
     *
     * <p>Отвечает {@code 200}; {@code 404} — шаблона нет ({@link EntityNotFoundException} в
     * сервисе); {@code 400} — статус не {@code DRAFT} и не {@code PUBLISHED} либо неизвестное
     * значение перечисления в теле.
     */
    @PutMapping("/{templateId}")
    @Operation(summary = "Обновить шаблон",
               description = "Обновляет шаблон. Черновик (DRAFT) правится на месте; обновление "
                       + "опубликованного шаблона создаёт НОВУЮ версию (ADR-023) со своим id, "
                       + "version + 1 и parentTemplateId, а прежняя версия снимается с публикации")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Шаблон обновлен",
                     content = @Content(schema = @Schema(implementation = TemplateDto.class))),
        @ApiResponse(responseCode = "400", description = "Шаблон не в статусе DRAFT и не PUBLISHED",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Шаблон не найден",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @Transactional
    public ResponseEntity<TemplateDto> updateTemplate(
            @Parameter(description = "ID шаблона", required = true)
            @PathVariable UUID templateId,
            @Valid @RequestBody UpdateTemplateRequest request) {

        Template updated = templateService.update(templateId, templateRequestMapper.toServiceRequest(request));
        return ResponseEntity.ok(templateMapper.toDto(updated));
    }

    /**
     * Публикует шаблон: {@link TemplateService#publish} проводит переход {@code PublishTemplate}
     * ({@code DRAFT → PUBLISHED}), а структуру проверяет движок — actions
     * {@code ValidateTemplateStructure} и {@code AllMandatorySlotsValid}, а не контроллер.
     *
     * <p>Отвечает {@code 200} с перечитанным шаблоном; {@code 404} — шаблона нет; {@code 409} —
     * переход не выполнен, то есть шаблон не в {@code DRAFT} либо структура не прошла проверку
     * ({@link IllegalStateException} из {@code okAfterTransition}). Отдельного {@code 400} для
     * «не тот статус» здесь нет: и ошибка клиента, и отказ guard'а сведены к {@code 409}.
     */
    @PostMapping("/{templateId}/publish")
    @Operation(summary = "Опубликовать шаблон",
               description = "Переводит шаблон DRAFT → PUBLISHED. Движок проверяет структуру "
                       + "(ValidateTemplateStructure) и заполненность обязательных слотов "
                       + "(AllMandatorySlotsValid)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Шаблон опубликован",
                     content = @Content(schema = @Schema(implementation = TemplateDto.class))),
        @ApiResponse(responseCode = "404", description = "Шаблон не найден",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Структура шаблона не прошла валидацию",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @Transactional
    public ResponseEntity<TemplateDto> publishTemplate(
            @Parameter(description = "ID шаблона", required = true)
            @PathVariable UUID templateId,
            @Valid @RequestBody PublishTemplateRequest request) {

        TransitionResult result = templateService.publish(templateId, request.actorId());
        return okAfterTransition(templateId, result, "PublishTemplate");
    }

    /**
     * Снимает шаблон с публикации: {@link TemplateService#deprecate} проводит переход
     * {@code DeprecateTemplate} ({@code PUBLISHED → DEPRECATED}).
     *
     * <p>Отвечает {@code 200}; {@code 404} — шаблона нет; {@code 409} — переход не выполнен
     * (шаблон не в {@code PUBLISHED}). Сам переход ничего не делает с уже запущенными процессами:
     * они продолжают работу по своей версии шаблона (Fork &amp; Drain, ADR-003).
     */
    @PostMapping("/{templateId}/deprecate")
    @Operation(summary = "Снять шаблон с публикации",
               description = "Переводит шаблон PUBLISHED → DEPRECATED. Новые процессы по нему не создаются, "
                       + "активные продолжают работу (Fork & Drain)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Шаблон снят с публикации",
                     content = @Content(schema = @Schema(implementation = TemplateDto.class))),
        @ApiResponse(responseCode = "404", description = "Шаблон не найден",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @Transactional
    public ResponseEntity<TemplateDto> deprecateTemplate(
            @Parameter(description = "ID шаблона", required = true)
            @PathVariable UUID templateId,
            @Valid @RequestBody DeprecateTemplateRequest request) {

        TransitionResult result = templateService.deprecate(templateId, request.actorId());
        return okAfterTransition(templateId, result, "DeprecateTemplate");
    }

    /**
     * Архивирует шаблон: {@link TemplateService#archive} проводит переход {@code ArchiveTemplate}
     * ({@code DEPRECATED → ARCHIVED}), а guard {@code NoActiveProcesses} проверяет, что на шаблоне
     * не осталось незавершённых процессов.
     *
     * <p>Отвечает {@code 200}; {@code 404} — шаблона нет; {@code 409} — переход не выполнен: в
     * ответе не различаются «есть активные процессы» и «шаблон не в {@code DEPRECATED}».
     */
    @PostMapping("/{templateId}/archive")
    @Operation(summary = "Архивировать шаблон",
               description = "Переводит шаблон DEPRECATED → ARCHIVED. Требует отсутствия активных "
                       + "процессов на этом шаблоне (guard NoActiveProcesses)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Шаблон архивирован",
                     content = @Content(schema = @Schema(implementation = TemplateDto.class))),
        @ApiResponse(responseCode = "404", description = "Шаблон не найден",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "На шаблоне есть активные процессы",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @Transactional
    public ResponseEntity<TemplateDto> archiveTemplate(
            @Parameter(description = "ID шаблона", required = true)
            @PathVariable UUID templateId,
            @Valid @RequestBody ArchiveTemplateRequest request) {

        TransitionResult result = templateService.archive(templateId, request.actorId());
        return okAfterTransition(templateId, result, "ArchiveTemplate");
    }

    /**
     * Форкает шаблон: {@link TemplateService#fork} создаёт <b>новую строку</b> с
     * {@code version + 1} и {@code parentTemplateId} в статусе {@code DRAFT} (ADR-023), исходный
     * шаблон не меняется. Это способ править опубликованный шаблон, не снимая его с публикации.
     *
     * <p>Отвечает {@code 201} с заголовком {@code Location} на новую версию; {@code 404} —
     * исходного шаблона нет. Тело запроса необязательно ({@code required = false}): без него
     * автором версии будет {@code null}.
     */
    @PostMapping("/{templateId}/fork")
    @Operation(summary = "Форк шаблона",
               description = "Создаёт новую версию шаблона: новая строка с version + 1 и "
                       + "parentTemplateId = id исходного, в статусе DRAFT (ADR-023). "
                       + "Исходный шаблон не изменяется")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Новая версия создана",
                     content = @Content(schema = @Schema(implementation = TemplateDto.class))),
        @ApiResponse(responseCode = "404", description = "Шаблон не найден",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @Transactional
    public ResponseEntity<TemplateDto> forkTemplate(
            @Parameter(description = "ID шаблона", required = true)
            @PathVariable UUID templateId,
            @Valid @RequestBody(required = false) ForkTemplateRequest request) {

        UUID actorId = request != null ? request.actorId() : null;
        Template forked = templateService.fork(templateId, actorId);

        return ResponseEntity.created(URI.create("/api/v1/admin/templates/" + forked.getId()))
                .body(templateMapper.toDto(forked));
    }

    /**
     * Шаблон по id — детально: этапы со слотами и правила применимости.
     *
     * <p>Отвечает {@code 200}; {@code 404} — шаблона нет ({@link EntityNotFoundException}).
     * Чтение помечено {@code readOnly = true}, потому что маппер обходит ленивые коллекции
     * {@code stages → actorSlots} и {@code applicabilityRules} при выключенном open-in-view.
     */
    @GetMapping("/{templateId}")
    @Operation(summary = "Получить шаблон",
               description = "Возвращает детальную информацию о шаблоне по ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Шаблон найден",
                     content = @Content(schema = @Schema(implementation = TemplateDto.class))),
        @ApiResponse(responseCode = "404", description = "Шаблон не найден",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @Transactional(readOnly = true)
    public ResponseEntity<TemplateDto> getTemplate(
            @Parameter(description = "ID шаблона", required = true)
            @PathVariable UUID templateId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("Template not found: " + templateId));
        return ResponseEntity.ok(templateMapper.toDto(template));
    }

    /**
     * Список шаблонов с необязательными фильтрами {@code status}, {@code processType} и
     * {@code name} (частичное совпадение без учёта регистра).
     *
     * <p>Отвечает {@code 200}; {@code 400} — неизвестное значение {@code status} или
     * {@code processType}: перечисления разбираются в контроллере <b>до</b> построения
     * {@link Specification}, иначе клиент получал бы {@code 500} из глубины запроса. Пагинации нет
     * — контракт §10.1 её не описывает, но ответ всегда возвращается целиком.
     */
    @GetMapping
    @Operation(summary = "Получить список шаблонов",
               description = "Возвращает список шаблонов с фильтрацией по статусу, типу процесса и имени")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Список шаблонов")
    })
    @Transactional(readOnly = true)
    public ResponseEntity<List<TemplateDto>> listTemplates(
            @Parameter(description = "Фильтр по статусу (DRAFT, PUBLISHED, DEPRECATED, ARCHIVED)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Фильтр по типу процесса (STANDARD, UNIFIED)")
            @RequestParam(required = false) String processType,
            @Parameter(description = "Фильтр по имени (частичное совпадение)")
            @RequestParam(required = false) String name) {

        var spec = Specification.allOf(
                statusFilter(status),
                processTypeFilter(processType),
                nameFilter(name));

        return ResponseEntity.ok(templateRepository.findAll(spec).stream()
                .map(templateMapper::toDto)
                .toList());
    }

    // ------------------------------------------------------------------ внутреннее
    /**
     * Возвращает актуальное состояние шаблона после перехода.
     *
     * <p>{@code performed=false} означает, что переход не найден для текущего статуса (например
     * публикация уже опубликованного шаблона) либо guard не пропустил. Первое — ошибка клиента
     * (409), второе — тоже 409: и то и другое требует вмешательства, а не молчаливого «ок».
     */
    private ResponseEntity<TemplateDto> okAfterTransition(
            UUID templateId, TransitionResult result, String transitionCode) {

        if (!result.performed()) {
            throw new IllegalStateException(
                    "Transition %s is not applicable for template %s in status %s"
                            .formatted(transitionCode, templateId, result.fromState()));
        }

        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("Template not found: " + templateId));

        return ResponseEntity.ok(templateMapper.toDto(template));
    }

    /**
     * Фильтр по статусу.
     *
     * <p>Значение разбирается ЗДЕСЬ, до создания лямбды {@link Specification}: внутри лямбды
     * исключение всплывает уже во время выполнения запроса, и Spring оборачивает его в
     * {@code InvalidDataAccessApiUsageException}, из-за чего клиент получал 500 вместо 400.
     */
    private Specification<Template> statusFilter(String status) {
        if (status == null || status.isBlank()) {
            return Specification.unrestricted();
        }
        LifecycleStatus parsed = parseStatus(status);
        return (root, query, cb) -> cb.equal(root.get("status"), parsed);
    }

    /**
     * Фильтр по типу процесса.
     *
     * <p>Значение разбирается до создания лямбды {@link Specification} по той же причине, что и в
     * {@link #statusFilter}: исключение внутри лямбды всплывает во время выполнения запроса и
     * превращается в {@code InvalidDataAccessApiUsageException}, то есть {@code 500} вместо
     * {@code 400}.
     */
    private Specification<Template> processTypeFilter(String processType) {
        if (processType == null || processType.isBlank()) {
            return Specification.unrestricted();
        }
        ProcessType parsed = parseProcessType(processType);
        return (root, query, cb) -> cb.equal(root.get("processType"), parsed);
    }

    private Specification<Template> nameFilter(String name) {
        if (name == null || name.isBlank()) {
            return Specification.unrestricted();
        }
        // Шаблон для LIKE готовится заранее по той же причине, что и разбор enum выше.
        String pattern = "%" + name.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern);
    }

    private LifecycleStatus parseStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        for (LifecycleStatus value : LifecycleStatus.values()) {
            if (value.name().equals(normalized)) {
                return value;
            }
        }
        throw new IllegalArgumentException(
                "Unknown template status '%s'; expected one of %s"
                        .formatted(status, Arrays.toString(LifecycleStatus.values())));
    }

    private ProcessType parseProcessType(String processType) {
        String normalized = processType.trim().toUpperCase(Locale.ROOT);
        for (ProcessType value : ProcessType.values()) {
            if (value.name().equals(normalized)) {
                return value;
            }
        }
        throw new IllegalArgumentException(
                "Unknown processType '%s'; expected one of %s"
                        .formatted(processType, Arrays.toString(ProcessType.values())));
    }
}
