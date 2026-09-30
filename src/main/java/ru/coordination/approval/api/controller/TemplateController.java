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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.coordination.approval.api.dto.template.*;
import ru.coordination.approval.api.exception.EntityNotFoundException;
import ru.coordination.approval.api.exception.ErrorResponse;
import ru.coordination.approval.api.mapper.TemplateMapper;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;

@RestController
@RequestMapping("/api/v1/admin/templates")
@RequiredArgsConstructor
@Tag(name = "Templates", description = "API для управления шаблонами процессов согласования")
public class TemplateController {

    private final TemplateRepository templateRepository;
    private final TemplateMapper templateMapper;

    @PostMapping
    @Operation(summary = "Создать шаблон",
               description = "Создает новый шаблон процесса согласования в статусе Draft")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Шаблон создан",
                     content = @Content(schema = @Schema(implementation = TemplateDto.class))),
        @ApiResponse(responseCode = "400", description = "Невалидные данные",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TemplateDto> createTemplate(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Данные для создания шаблона")
            @Valid @RequestBody CreateTemplateRequest request) {
        // TODO: Implement template creation
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PutMapping("/{templateId}")
    @Operation(summary = "Обновить шаблон",
               description = "Обновляет существующий шаблон. Если шаблон опубликован, создается новая версия")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Шаблон обновлен",
                     content = @Content(schema = @Schema(implementation = TemplateDto.class))),
        @ApiResponse(responseCode = "404", description = "Шаблон не найден",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TemplateDto> updateTemplate(
            @Parameter(description = "ID шаблона", required = true)
            @PathVariable UUID templateId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Данные для обновления шаблона")
            @Valid @RequestBody UpdateTemplateRequest request) {
        // TODO: Implement template update
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PostMapping("/{templateId}/publish")
    @Operation(summary = "Опубликовать шаблон",
               description = "Переводит шаблон из статуса Draft в Published после валидации")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Шаблон опубликован",
                     content = @Content(schema = @Schema(implementation = TemplateDto.class))),
        @ApiResponse(responseCode = "404", description = "Шаблон не найден",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Шаблон не прошел валидацию",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TemplateDto> publishTemplate(
            @Parameter(description = "ID шаблона", required = true)
            @PathVariable UUID templateId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "ID актора, публикующего шаблон")
            @Valid @RequestBody PublishTemplateRequest request) {
        // TODO: Implement template publish
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PostMapping("/{templateId}/deprecate")
    @Operation(summary = "Снять с публикации",
               description = "Переводит шаблон из статуса Published в Deprecated")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Шаблон снят с публикации",
                     content = @Content(schema = @Schema(implementation = TemplateDto.class))),
        @ApiResponse(responseCode = "404", description = "Шаблон не найден",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TemplateDto> deprecateTemplate(
            @Parameter(description = "ID шаблона", required = true)
            @PathVariable UUID templateId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "ID актора, снимающего шаблон с публикации")
            @Valid @RequestBody DeprecateTemplateRequest request) {
        // TODO: Implement template deprecation
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PostMapping("/{templateId}/archive")
    @Operation(summary = "Архивировать шаблон",
               description = "Переводит шаблон из статуса Deprecated в Archived")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Шаблон архивирован",
                     content = @Content(schema = @Schema(implementation = TemplateDto.class))),
        @ApiResponse(responseCode = "404", description = "Шаблон не найден",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TemplateDto> archiveTemplate(
            @Parameter(description = "ID шаблона", required = true)
            @PathVariable UUID templateId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "ID актора, архивирующего шаблон")
            @Valid @RequestBody ArchiveTemplateRequest request) {
        // TODO: Implement template archiving
        throw new UnsupportedOperationException("Not implemented yet");
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
    public ResponseEntity<TemplateDto> getTemplate(
            @Parameter(description = "ID шаблона", required = true)
            @PathVariable UUID templateId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("Template not found: " + templateId));
        return ResponseEntity.ok(templateMapper.toDto(template));
    }

    @GetMapping
    @Operation(summary = "Получить список шаблонов",
               description = "Возвращает список шаблонов с возможностью фильтрации по статусу и имени")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Список шаблонов")
    })
    public ResponseEntity<List<TemplateDto>> listTemplates(
            @Parameter(description = "Фильтр по статусу (DRAFT, PUBLISHED, DEPRECATED, ARCHIVED)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Фильтр по имени (частичное совпадение)")
            @RequestParam(required = false) String name) {
        List<Template> templates = templateRepository.findAll();
        return ResponseEntity.ok(templates.stream().map(templateMapper::toDto).toList());
    }
}
