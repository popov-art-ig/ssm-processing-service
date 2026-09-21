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

@RestController
@RequestMapping("/api/v1/processes/{processId}")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "API для управления комментариями")
public class CommentController {

    private final CommentRepository commentRepository;
    private final CommentMapper commentMapper;

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
