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
import ru.coordination.approval.api.dto.comment.CommentDto;
import ru.coordination.approval.api.dto.comment.CreateCommentRequest;
import ru.coordination.approval.api.exception.ErrorResponse;
import ru.coordination.approval.api.mapper.CommentMapper;
import ru.coordination.approval.domain.process.Comment;
import ru.coordination.approval.domain.process.CommentRepository;

/**
 * Комментарии процесса — {@code 07_api_contract.md} §18 (группа 9 «Комментарии»), префикс
 * {@code /api/v1/processes/{processId}}.
 *
 * <p>Чтение ({@link #listComments}) отдаёт комментарии процесса напрямую через
 * {@link CommentRepository} без view-сервиса: маппинг {@link CommentMapper} не заходит ни в одну
 * ленивую связь, кроме {@code stage}, поэтому отдельная транзакция на чтение не нужна.
 * {@link #createComment} — <b>заглушка</b>, см. её Javadoc.
 *
 * <p>Комментарии в системе пишет не REST, а action {@code RecordComment} перехода {@code Decide}
 * (PHASE-05): текст решения сохраняется в таблицу {@code comment}.
 */
@RestController
@RequestMapping("/api/v1/processes/{processId}")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "API для управления комментариями")
public class CommentController {

    private final CommentRepository commentRepository;
    private final CommentMapper commentMapper;

    /**
     * <b>Не реализован.</b> Бросает {@code UnsupportedOperationException("Not implemented yet")} —
     * клиент получает {@code 500 INTERNAL_ERROR} вместо {@code 201}.
     *
     * <p>Создавать комментарий нечем: сервиса комментариев в проекте нет, а action
     * {@code RecordComment} пишет строку в {@code comment} только как часть перехода
     * {@code Decide} (PHASE-05) и вызывается движком, а не контроллером. Кроме того,
     * {@link CreateCommentRequest} содержит {@code remarkId}, которого в сущности
     * {@link Comment} нет: связь «комментарий → замечание» в модели отсутствует, поэтому при
     * реализации эндпоинта её придётся либо добавить, либо отбросить поле.
     */
    @PostMapping("/comments")
    @Operation(summary = "Создать комментарий",
               description = "Добавляет комментарий к процессу, этапу или замечанию")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Комментарий создан",
                     content = @Content(schema = @Schema(implementation = CommentDto.class))),
        @ApiResponse(responseCode = "400", description = "Невалидные данные",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CommentDto> createComment(
            @Parameter(description = "ID процесса", required = true)
            @PathVariable UUID processId,
            @Valid @RequestBody CreateCommentRequest request) {
        // TODO: Implement comment creation
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Комментарии процесса — вторая половина группы §18 и единственный работающий эндпоинт этого
     * контроллера.
     *
     * <p>Отвечает {@code 200}. Параметры {@code stageId} и {@code remarkId} принимаются, но в
     * выборке не участвуют: всегда вызывается {@link CommentRepository#findByProcessId}. Фильтр по
     * этапу можно было бы применить в памяти (в {@link Comment} есть {@code stage}), а по
     * замечанию — нельзя, связи с {@link Remark} у комментария нет. Отсутствие фильтрации —
     * известное расхождение с §18.2. Процесс не проверяется: несуществующий {@code processId}
     * даёт пустой список, а не {@code 404}.
     */
    @GetMapping("/comments")
    @Operation(summary = "Получить список комментариев",
               description = "Возвращает комментарии с возможностью фильтрации по этапу или замечанию")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Список комментариев")
    })
    public ResponseEntity<List<CommentDto>> listComments(
            @Parameter(description = "ID процесса", required = true)
            @PathVariable UUID processId,
            @Parameter(description = "Фильтр по этапу")
            @RequestParam(required = false) UUID stageId,
            @Parameter(description = "Фильтр по замечанию")
            @RequestParam(required = false) UUID remarkId) {

        List<Comment> comments = commentRepository.findByProcessId(processId);
        return ResponseEntity.ok(comments.stream().map(commentMapper::toDto).toList());
    }
}
