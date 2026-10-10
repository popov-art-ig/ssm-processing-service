package ru.coordination.approval.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.coordination.approval.api.dto.decision.DecisionDto;
import ru.coordination.approval.api.exception.ErrorResponse;
import ru.coordination.approval.api.mapper.DecisionMapper;
import ru.coordination.approval.api.service.DecisionQueryService;

/**
 * Список решений по процессу — {@code GET /api/v1/processes/{processId}/decisions}
 * ({@code 07_api_contract.md} §16.2, решения этапа).
 *
 * <p>Вынесен отдельным контроллером, потому что {@link DecisionController} привязан к
 * вложенному пути {@code /processes/{processId}/stages/{stageId}/participants/{participantId}} —
 * на этом уровне сегмента {@code decisions} не существует.
 */
@RestController
@RequestMapping("/api/v1/processes/{processId}/decisions")
@RequiredArgsConstructor
@Tag(name = "Decisions", description = "API для принятия решений участниками")
public class ProcessDecisionController {

    private final DecisionQueryService decisionQueryService;
    private final DecisionMapper decisionMapper;

    /**
     * Все решения процесса — история голосования целиком, собранная запросом
     * {@code DecisionRepository.findByProcessId} (JOIN через участника, итерацию и этап).
     *
     * <p>Отвечает {@code 200}; {@code 404} не бросается никогда, хотя Swagger-аннотация его
     * обещает: отсутствие процесса даёт пустой список, а не ошибку. Обращение
     * {@link DecisionMapper} к {@code decision.participant} безопасно: у ленивого прокси
     * идентификатор читается без инициализации, поэтому отдельная транзакция не нужна.
     */
    @GetMapping
    @Operation(summary = "Получить решения процесса",
               description = "Возвращает все решения, принятые участниками процесса")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Список решений",
                     content = @Content(schema = @Schema(implementation = DecisionDto.class))),
        @ApiResponse(responseCode = "404", description = "Процесс не найден",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<DecisionDto>> listDecisions(
            @Parameter(description = "ID процесса", required = true)
            @PathVariable UUID processId) {
        List<DecisionDto> decisions = decisionQueryService.listByProcessId(processId).stream()
                .map(decisionMapper::toDto)
                .toList();
        return ResponseEntity.ok(decisions);
    }
}
