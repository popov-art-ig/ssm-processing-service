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
import ru.coordination.approval.api.exception.ErrorResponse;
import ru.coordination.approval.api.mapper.DecisionMapper;
import ru.coordination.approval.api.service.DecisionQueryService;
import ru.coordination.approval.domain.process.Decision;

/**
 * Решения участников — {@code 07_api_contract.md} §16 (группа 7 «Решения»), префикс
 * {@code /api/v1/processes/{processId}/stages/{stageId}/participants/{participantId}}.
 *
 * <p>Принятие решения делегируется {@link DecisionQueryService#decide} → доменному
 * {@code DecisionService}: движок выполняет переход {@code Decide} участника, action
 * {@code RecordDecision} пишет строку в {@code decision}, а агрегация решений этапа
 * ({@code ALL}/{@code ANY}/{@code MAJORITY}) запускается следом переходами {@code StageApproved}/
 * {@code StageOnRework} — то есть решение участника может закрыть этап и активировать следующий.
 *
 * <p>Путь отличается от контракта: §16.1 описывает
 * {@code POST /processes/{id}/stages/{stageId}/decisions} с {@code participantId} в теле, здесь
 * участник — сегмент пути. Список решений процесса вынесен в {@link ProcessDecisionController}:
 * на этом уровне сегмента {@code decisions} не существует.
 */
@RestController
@RequestMapping("/api/v1/processes/{processId}/stages/{stageId}/participants/{participantId}")
@RequiredArgsConstructor
@Tag(name = "Decisions", description = "API для принятия решений участниками")
public class DecisionController {

    private final DecisionQueryService decisionQueryService;
    private final DecisionMapper decisionMapper;

    /**
     * Принимает решение участника: {@link DecisionQueryService#decide} выполняет переход
     * {@code Decide} (участник получает статус {@code Decided}, action {@code RecordDecision}
     * пишет строку в {@code decision}, action {@code RecordComment} — текст в {@code comment}), а
     * затем перечитывает сохранённую запись, поэтому {@code recordedAt} в ответе заполнен
     * движком, а не контроллером.
     *
     * <p>Отвечает {@code 200}. {@code 400} — тело не прошло {@code @Valid} либо тип решения не
     * принят guard'ом {@code IsValidDecisionType}; {@code 404} — участник не найден либо решение
     * не записалось, то есть переход не выполнен ({@code EntityNotFoundException} из
     * {@link DecisionQueryService}). Решение последнего участника этапа запускает агрегацию и
     * может закрыть этап, активировав следующий, — то есть один вызов меняет состояние всего
     * процесса.
     */
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
        Decision decision = decisionQueryService.decide(
                processId,
                stageId,
                participantId,
                request.decision(),
                request.comment(),
                request.actorId());
        return ResponseEntity.ok(decisionMapper.toDto(decision));
    }

    /**
     * Возвращает запись {@code decision} участника — одно решение без истории и без проверки,
     * что этап и процесс в пути совпадают с решением.
     *
     * <p>Отвечает {@code 200}; {@code 404} — решения по участнику нет
     * ({@code EntityNotFoundException} из {@link DecisionQueryService#getByParticipantId}).
     * Известное ограничение: {@code findByParticipantId} возвращает {@code Optional}, а в
     * {@code decision} нет уникального ограничения на {@code participant_id} (V7 создаёт только
     * индекс), поэтому повторное решение того же участника — например после возврата на
     * доработку — даст не 200, а ошибку размера результата.
     */
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
        Decision decision = decisionQueryService.getByParticipantId(participantId);
        return ResponseEntity.ok(decisionMapper.toDto(decision));
    }
}
