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
import ru.coordination.approval.api.dto.decision.DecideRequest;
import ru.coordination.approval.api.dto.decision.DecisionDto;
import ru.coordination.approval.api.exception.EntityNotFoundException;
import ru.coordination.approval.api.exception.ErrorResponse;
import ru.coordination.approval.api.mapper.DecisionMapper;
import ru.coordination.approval.domain.process.Decision;
import ru.coordination.approval.domain.process.DecisionRepository;

@RestController
@RequestMapping("/api/v1/processes/{processId}/stages/{stageId}/participants/{participantId}")
@RequiredArgsConstructor
@Tag(name = "Decisions", description = "API для принятия решений участниками")
public class DecisionController {

    private final DecisionRepository decisionRepository;
    private final DecisionMapper decisionMapper;

    @PostMapping("/decide")
    @Operation(summary = "Принять решение",
               description = "Участник принимает решение по этапу согласования (APPROVE, APPROVE_WITH_COMMENTS, REJECT)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Решение успешно принято",
                     content = @Content(schema = @Schema(implementation = DecisionDto.class))),
        @ApiResponse(responseCode = "400", description = "Невалидные данные запроса",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Участник не найден",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<DecisionDto> decide(
            @Parameter(description = "ID процесса", required = true)
            @PathVariable UUID processId,
            @Parameter(description = "ID этапа", required = true)
            @PathVariable UUID stageId,
            @Parameter(description = "ID участника", required = true)
            @PathVariable UUID participantId,
            @Valid @RequestBody DecideRequest request) {
        // TODO: Implement decision making
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @GetMapping("/decision")
    @Operation(summary = "Получить решение участника",
               description = "Возвращает решение, принятое участником")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Решение найдено",
                     content = @Content(schema = @Schema(implementation = DecisionDto.class))),
        @ApiResponse(responseCode = "404", description = "Решение не найдено",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<DecisionDto> getDecision(
            @Parameter(description = "ID участника", required = true)
            @PathVariable UUID participantId) {
        Decision decision = decisionRepository.findById(participantId)
                .orElseThrow(() -> new EntityNotFoundException("Decision not found for participant: " + participantId));
        return ResponseEntity.ok(decisionMapper.toDto(decision));
    }
}
