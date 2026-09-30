package ru.coordination.approval.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.coordination.approval.api.dto.process.*;
import ru.coordination.approval.api.exception.EntityNotFoundException;
import ru.coordination.approval.api.exception.ErrorResponse;
import ru.coordination.approval.api.mapper.ProcessMapper;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.ProcessRepository;
import ru.coordination.approval.service.ProcessService;

@RestController
@RequestMapping("/api/v1/processes")
@RequiredArgsConstructor
@Tag(name = "Processes", description = "API для управления процессами согласования")
public class ProcessController {

    private final ProcessService processService;
    private final ProcessRepository processRepository;
    private final ProcessMapper processMapper;

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
        // TODO: Implement process creation from entity snapshot
        // 1. Create EntitySnapshot from request
        // 2. MatchService.findMatchingTemplates()
        // 3. RouteGeneratorService.generateRoute()
        // 4. Map to ProcessDto
        throw new UnsupportedOperationException("Not implemented yet");
    }

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
        ProcessInstance process = processRepository.findById(processId)
                .orElseThrow(() -> new EntityNotFoundException("Process not found: " + processId));
        return ResponseEntity.ok(processMapper.toDto(process));
    }

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
        // TODO: Implement process start
        throw new UnsupportedOperationException("Not implemented yet");
    }

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
        // TODO: Implement process recall
        throw new UnsupportedOperationException("Not implemented yet");
    }

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
        // TODO: Implement process resume after rework
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @GetMapping
    @Operation(summary = "Получить список процессов",
               description = "Возвращает постраничный список процессов с возможностью фильтрации")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Список процессов",
                     content = @Content(schema = @Schema(implementation = Page.class)))
    })
    public ResponseEntity<Page<ProcessDto>> listProcesses(
            @Parameter(description = "Фильтр по статусу")
            @RequestParam(required = false) String status,
            @Parameter(description = "Фильтр по инициатору")
            @RequestParam(required = false) UUID initiatorId,
            @Parameter(description = "Фильтр по типу сущности")
            @RequestParam(required = false) String entityType,
            @Parameter(description = "Номер страницы (начиная с 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы")
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<ProcessInstance> processes = processRepository.findAll(pageable);
        return ResponseEntity.ok(processes.map(processMapper::toDto));
    }
}
