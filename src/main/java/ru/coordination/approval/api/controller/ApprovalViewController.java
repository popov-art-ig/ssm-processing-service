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

/**
 * Агрегированные представления для UI — {@code 07_api_contract.md} §25 (группа 16
 * «Агрегированные эндпоинты для Front», ADR-020), префикс {@code /api/v1/approval-view}.
 *
 * <p>Каждый эндпоинт отдаёт готовую модель экрана одним запросом: агрегация целиком в
 * {@link ApprovalViewService}, который batch-загрузкой участников и решений заменяет обход
 * связей в цикле (целевое требование — ответ по процессу с 10 этапами быстрее 500 мс).
 *
 * <p>Форма путей отличается от контракта: §25.2 описывает {@code GET /entities/approval-view} с
 * параметрами {@code entityType}/{@code entityId}/{@code detailLevel}, а {@code /tasks/my} и
 * {@code /processes/my} в §25 отсутствуют вовсе (там {@code approval-actions} и
 * {@code approval-remarks}).
 */
@RestController
@RequestMapping("/api/v1/approval-view")
@RequiredArgsConstructor
@Tag(name = "Approval View", description = "Агрегированные эндпоинты для оптимизации UI")
public class ApprovalViewController {

    private final ApprovalViewService approvalViewService;

    /**
     * Собирает экран согласования целиком — процесс, этапы с участниками и их решениями, замечания
     * и комментарии — через {@link ApprovalViewService#getApprovalView}.
     *
     * <p>Отвечает {@code 200}; {@code 404} — по паре {@code entityType}/{@code entityId} процесс не
     * найден ({@link EntityNotFoundException}). Известные ограничения относительно §25.2: активный
     * процесс не выбирается (при нескольких процессах на одну сущность
     * {@code findByEntityTypeAndEntityId} бросит {@code IncorrectResultSizeDataAccessException},
     * то есть {@code 500}), а параметры {@code detailLevel}/{@code full}/{@code role}, которыми
     * контракт управляет объёмом ответа, не поддерживаются — ответ всегда полный.
     */
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

    /**
     * Задачи пользователя — участники в статусе {@code Assigned} со всей обвязкой
     * (процесс, этап, срок) в одной странице, через {@link ApprovalViewService#getMyTasks}.
     *
     * <p>Отвечает {@code 200}; {@code 404} невозможен — пустой результат остаётся пустой
     * страницей. Идентификатор задачи в ответе — это id участника: отдельной сущности «задача» в
     * модели нет, а {@code TaskDto} склеивает {@code Participant}, {@code StageInstance} и
     * {@code ProcessInstance} (§25, ADR-020).
     */
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

    /**
     * Процессы пользователя — где он инициатор или ответственный — в виде кратких карточек для
     * списка, через {@link ApprovalViewService#getMyProcesses}.
     *
     * <p>Отвечает {@code 200}; условие «или» накладывается на обоих полях сразу
     * ({@code findByInitiatorIdOrResponsibleUserId}), поэтому один и тот же пользователь может
     * получить процесс дважды, если он и инициатор, и ответственный. Представление намеренно
     * короче детального: {@code currentStageName} — имя первого этапа в статусе {@code Active}, а
     * {@code dueDate} всегда {@code null} (в сервисе помечено TODO).
     */
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
