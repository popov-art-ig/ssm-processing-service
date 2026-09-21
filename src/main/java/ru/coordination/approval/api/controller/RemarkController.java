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
import ru.coordination.approval.api.dto.remark.*;
import ru.coordination.approval.api.exception.EntityNotFoundException;
import ru.coordination.approval.api.exception.ErrorResponse;
import ru.coordination.approval.api.mapper.RemarkMapper;
import ru.coordination.approval.domain.process.Remark;
import ru.coordination.approval.domain.process.RemarkRepository;

@RestController
@RequestMapping("/api/v1/processes/{processId}/remarks")
@RequiredArgsConstructor
@Tag(name = "Remarks", description = "API для управления замечаниями")
public class RemarkController {

    private final RemarkRepository remarkRepository;
    private final RemarkMapper remarkMapper;

    @PostMapping
    @Operation(summary = "Создать замечание",
               description = "Создает новое замечание к процессу согласования")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Замечание создано",
                     content = @Content(schema = @Schema(implementation = RemarkDto.class))),
        @ApiResponse(responseCode = "400", description = "Невалидные данные",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RemarkDto> createRemark(
            @Parameter(description = "ID процесса", required = true)
            @PathVariable UUID processId,
            @Valid @RequestBody CreateRemarkRequest request) {
        // TODO: Implement remark creation
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PutMapping("/{remarkId}/resolve")
    @Operation(summary = "Исправить замечание",
               description = "Переводит замечание из статуса Proposed в Resolved")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Замечание исправлено",
                     content = @Content(schema = @Schema(implementation = RemarkDto.class))),
        @ApiResponse(responseCode = "404", description = "Замечание не найдено",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RemarkDto> resolveRemark(
            @Parameter(description = "ID замечания", required = true)
            @PathVariable UUID remarkId,
            @Valid @RequestBody ResolveRemarkRequest request) {
        // TODO: Implement remark resolution
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PutMapping("/{remarkId}/reject")
    @Operation(summary = "Отклонить замечание",
               description = "Переводит замечание из статуса Proposed в Rejected")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Замечание отклонено",
                     content = @Content(schema = @Schema(implementation = RemarkDto.class))),
        @ApiResponse(responseCode = "404", description = "Замечание не найдено",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RemarkDto> rejectRemark(
            @Parameter(description = "ID замечания", required = true)
            @PathVariable UUID remarkId,
            @Valid @RequestBody RejectRemarkRequest request) {
        // TODO: Implement remark rejection
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PutMapping("/{remarkId}/accept")
    @Operation(summary = "Принять исправление замечания",
               description = "Переводит замечание из статуса Resolved в Accepted")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Исправление принято",
                     content = @Content(schema = @Schema(implementation = RemarkDto.class))),
        @ApiResponse(responseCode = "404", description = "Замечание не найдено",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RemarkDto> acceptRemark(
            @Parameter(description = "ID замечания", required = true)
            @PathVariable UUID remarkId,
            @Valid @RequestBody AcceptRemarkRequest request) {
        // TODO: Implement resolution acceptance
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PutMapping("/{remarkId}/reject-resolution")
    @Operation(summary = "Отклонить исправление замечания",
               description = "Возвращает замечание из статуса Resolved обратно в Proposed")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Исправление отклонено",
                     content = @Content(schema = @Schema(implementation = RemarkDto.class))),
        @ApiResponse(responseCode = "404", description = "Замечание не найдено",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RemarkDto> rejectResolution(
            @Parameter(description = "ID замечания", required = true)
            @PathVariable UUID remarkId,
            @Valid @RequestBody RejectResolutionRequest request) {
        // TODO: Implement resolution rejection
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @GetMapping
    @Operation(summary = "Получить список замечаний",
               description = "Возвращает список замечаний по процессу с возможностью фильтрации по статусу")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Список замечаний")
    })
    public ResponseEntity<List<RemarkDto>> listRemarks(
            @Parameter(description = "ID процесса", required = true)
            @PathVariable UUID processId,
            @Parameter(description = "Фильтр по статусу (PROPOSED, RESOLVED, ACCEPTED, REJECTED)")
            @RequestParam(required = false) String status) {
        List<Remark> remarks;
        if (status != null) {
            remarks = remarkRepository.findByProcessIdAndStatus(processId, status);
        } else {
            remarks = remarkRepository.findByProcessId(processId);
        }
        return ResponseEntity.ok(remarks.stream().map(remarkMapper::toDto).toList());
    }
}
