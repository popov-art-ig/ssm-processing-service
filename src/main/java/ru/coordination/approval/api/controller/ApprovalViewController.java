package ru.coordination.approval.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.coordination.approval.api.dto.view.*;
import ru.coordination.approval.api.exception.EntityNotFoundException;
import ru.coordination.approval.api.exception.ErrorResponse;
import ru.coordination.approval.api.service.ApprovalViewService;

@RestController
@RequestMapping("/api/v1/approval-view")
@RequiredArgsConstructor
@Tag(name = "Approval View", description = "Агрегированные эндпоинты для оптимизации UI")
public class ApprovalViewController {

    private final ApprovalViewService approvalViewService;

    @GetMapping("/entities/{entityId}")
    @Operation(summary = "Получить полную информацию о процессе согласования",
               description = "Возвращает процесс + этапы + участники + решения + замечания в одном запросе (оптимизировано для UI)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Данные получены",
                     content = @Content(schema = @Schema(implementation = ApprovalViewDto.class))),
        @ApiResponse(responseCode = "404", description = "Процесс не найден",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApprovalViewDto> getApprovalView(
            @Parameter(description = "ID сущности", required = true)
            @PathVariable UUID entityId,
            @Parameter(description = "Тип сущности", required = true, example = "CONTRACT")
            @RequestParam String entityType) {
        ApprovalViewDto view = approvalViewService.getApprovalView(entityType, entityId);
        return ResponseEntity.ok(view);
    }

    @GetMapping("/tasks/my")
    @Operation(summary = "Получить задачи текущего пользователя",
               description = "Возвращает список участников в статусе Assigned для указанного пользователя")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Список задач",
                     content = @Content(schema = @Schema(implementation = Page.class)))
    })
    public ResponseEntity<Page<TaskDto>> getMyTasks(
            @Parameter(description = "ID пользователя", required = true)
            @RequestParam UUID userId,
            @Parameter(description = "Номер страницы")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы")
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<TaskDto> tasks = approvalViewService.getMyTasks(userId, pageable);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/processes/my")
    @Operation(summary = "Получить процессы текущего пользователя",
               description = "Возвращает список процессов, где пользователь является инициатором или ответственным")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Список процессов",
                     content = @Content(schema = @Schema(implementation = Page.class)))
    })
    public ResponseEntity<Page<ProcessSummaryDto>> getMyProcesses(
            @Parameter(description = "ID пользователя", required = true)
            @RequestParam UUID userId,
            @Parameter(description = "Фильтр по статусу")
            @RequestParam(required = false) String status,
            @Parameter(description = "Номер страницы")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы")
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<ProcessSummaryDto> processes = approvalViewService.getMyProcesses(userId, status, pageable);
        return ResponseEntity.ok(processes);
    }
}
