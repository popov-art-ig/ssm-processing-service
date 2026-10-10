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

/**
 * Замечания процесса — {@code 07_api_contract.md} §17 (группа 8 «Замечания»), префикс
 * {@code /api/v1/processes/{processId}/remarks}.
 *
 * <p>Из группы реализовано только чтение — {@link #listRemarks}. Остальные пять эндпоинтов
 * ({@code POST /remarks}, {@code /resolve}, {@code /reject}, {@code /accept},
 * {@code /reject-resolution}) — <b>заглушки</b>, см. их Javadoc.
 */
@RestController
@RequestMapping("/api/v1/processes/{processId}/remarks")
@RequiredArgsConstructor
@Tag(name = "Remarks", description = "API для управления замечаниями")
public class RemarkController {

    private final RemarkRepository remarkRepository;
    private final RemarkMapper remarkMapper;

    /**
     * <b>Не реализован.</b> Бросает {@code UnsupportedOperationException("Not implemented yet")};
     * отдельной ветки для неё в обработчике исключений нет, поэтому срабатывает перехват
     * {@code Exception} и клиент получает {@code 500 INTERNAL_ERROR} вместо заявленного в
     * Swagger-аннотации {@code 201}.
     *
     * <p>Отсутствует привязка к уже написанному {@code RemarkService#createRemark} (PHASE-11):
     * сервис умеет создать замечание и провести переход {@code CreateRemark} карты
     * {@code RemarkStateMachine} (V24). Пока вызов не добавлен, замечание через API создать
     * нельзя — ни в статусе {@code Draft}, ни в {@code Open}.
     */
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

    /**
     * <b>Не реализован.</b> Бросает {@code UnsupportedOperationException("Not implemented yet")} —
     * {@code 500 INTERNAL_ERROR} вместо {@code 200} и без обещанного {@code 404}.
     *
     * <p>Привязать есть к чему: {@code RemarkService#processRemark}(remarkId, resolution, actorId)
     * проводит переход {@code ProcessRemark} карты {@code RemarkStateMachine} (V24). Расхождение
     * терминологическое: Swagger и контракт говорят о {@code Proposed → Resolved}, а в реестре
     * статусов для {@code REMARK} зарегистрированы {@code Proposed}/{@code Active}/{@code InProgress}/
     * {@code Processed}/{@code Rejected}/{@code NotRequired} — статуса {@code Resolved} нет, и
     * {@code resolutionText} из {@link ResolveRemarkRequest} некуда положить: в сущности
     * {@link Remark} хранятся только {@code resolvedAt} и {@code resolvedBy}.
     */
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

    /**
     * <b>Не реализован.</b> Бросает {@code UnsupportedOperationException("Not implemented yet")} —
     * {@code 500 INTERNAL_ERROR} вместо {@code 200}.
     *
     * <p>Привязка готова: {@code RemarkService#rejectRemark}(remarkId, reason, actorId) проводит
     * переход {@code RejectRemark} (V24) и пишет причину в {@code Remark.rejectionReason}, а
     * {@code IsRemarkRejection} отличает отклонение от обработки по наличию {@code reason} в
     * параметрах перехода. Не хватает только вызова контроллера, поэтому {@code reason} из
     * {@link RejectRemarkRequest} сейчас никуда не передаётся.
     */
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

    /**
     * <b>Не реализован.</b> Бросает {@code UnsupportedOperationException("Not implemented yet")} —
     * {@code 500 INTERNAL_ERROR} вместо {@code 200}.
     *
     * <p>Привязывать не к чему: в {@code RemarkService} нет метода принятия исправления, а в карте
     * {@code RemarkStateMachine} (V24) — перехода из обработанного замечания в принятое. Статуса
     * {@code Accepted} нет и в реестре статусов для {@code REMARK} ({@code Proposed}, {@code Active},
     * {@code InProgress}, {@code Processed}, {@code Rejected}, {@code NotRequired}), поэтому
     * эндпоинт по контракту §17 нельзя включить одним вызовом сервиса: нужны новый статус,
     * переход и action.
     */
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

    /**
     * <b>Не реализован.</b> Бросает {@code UnsupportedOperationException("Not implemented yet")} —
     * {@code 500 INTERNAL_ERROR} вместо {@code 200}.
     *
     * <p>Обратного перехода «исправление отклонено» нет ни в {@code RemarkService} (там только
     * {@code processRemark} и {@code rejectRemark}), ни в карте {@code RemarkStateMachine} (V24).
     * Поэтому {@code reason} из {@link RejectResolutionRequest} сейчас некуда положить, а
     * {@link Remark} не получит ни нового статуса, ни отметки о возврате в работу — под
     * {@code rejectionReason} понимается причина отклонения самого замечания.
     */
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

    /**
     * Список замечаний процесса с необязательным фильтром по статусу — единственный работающий
     * эндпоинт группы.
     *
     * <p>Отвечает {@code 200}. Читается напрямую через {@link RemarkRepository}
     * ({@code findByProcessId}/{@code findByProcessIdAndStatus}) и маппится {@link RemarkMapper}:
     * переходы и {@code audit_event} для чтения не нужны, поэтому сервисный слой не привлекается.
     * Фильтр по статусу передаётся строкой без разбора перечисления — неизвестное значение даёт
     * пустой список, а не {@code 400}. Существование процесса не проверяется: неизвестный
     * {@code processId} — тоже пустой список, а не {@code 404}.
     */
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
