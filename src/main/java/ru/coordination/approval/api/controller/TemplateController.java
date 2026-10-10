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
