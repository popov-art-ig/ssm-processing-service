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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ru.coordination.approval.api.dto.statemachine.*;
import ru.coordination.approval.api.exception.EntityNotFoundException;
import ru.coordination.approval.api.exception.ErrorResponse;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.engine.model.StateMachineConfigRepository;
import ru.coordination.approval.engine.validation.TransitionMapValidator;
import ru.coordination.approval.engine.validation.TransitionMapViolation;

@RestController
@RequestMapping("/api/v1/admin/state-machines")
@RequiredArgsConstructor
@Tag(name = "State Machine Configuration", description = "API для конфигурации конечных автоматов")
public class StateMachineConfigController {

    private final StateMachineConfigRepository stateMachineConfigRepository;
    private final TransitionMapValidator transitionMapValidator;

    @GetMapping
    @Operation(summary = "Получить список конфигураций",
               description = "Возвращает все конфигурации конечных автоматов")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Список конфигураций")
    })
    @Transactional(readOnly = true)
    public ResponseEntity<List<StateMachineConfigDto>> listConfigs() {
        List<StateMachineConfig> configs = stateMachineConfigRepository.findAll();
        return ResponseEntity.ok(configs.stream().map(this::toDto).toList());
    }

    @GetMapping("/{configId}")
    @Operation(summary = "Получить конфигурацию",
               description = "Возвращает детальную информацию о конфигурации конечного автомата")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Конфигурация найдена",
                     content = @Content(schema = @Schema(implementation = StateMachineConfigDto.class))),
        @ApiResponse(responseCode = "404", description = "Конфигурация не найдена",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @Transactional(readOnly = true)
    public ResponseEntity<StateMachineConfigDto> getConfig(
            @Parameter(description = "ID конфигурации", required = true)
            @PathVariable UUID configId) {
        StateMachineConfig config = stateMachineConfigRepository.findById(configId)
                .orElseThrow(() -> new EntityNotFoundException("Config not found: " + configId));
        return ResponseEntity.ok(toDto(config));
    }

    @PostMapping("/{configId}/validate")
    @Operation(summary = "Проверить карту переходов",
               description = "Валидирует карту по 05_guards_actions_registry.md §7: наличие guards/actions "
                       + "в реестрах, известность событий, ссылки на состояния, однозначность выбора "
                       + "перехода и применимость guards/actions к типу сущности")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Результат проверки",
                     content = @Content(schema = @Schema(implementation = TransitionMapValidationDto.class))),
        @ApiResponse(responseCode = "404", description = "Конфигурация не найдена",
                     content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TransitionMapValidationDto> validateConfig(
            @Parameter(description = "ID конфигурации", required = true)
            @PathVariable UUID configId) {
        StateMachineConfig config = stateMachineConfigRepository.findById(configId)
                .orElseThrow(() -> new EntityNotFoundException("Config not found: " + configId));

        List<TransitionMapViolation> violations = transitionMapValidator.validate(config);

        return ResponseEntity.ok(new TransitionMapValidationDto(
                configId,
                violations.isEmpty(),
                violations.stream()
                        .map(v -> new TransitionMapViolationDto(v.code().name(), v.where(), v.message()))
                        .toList()));
    }

    private StateMachineConfigDto toDto(StateMachineConfig config) {
        return new StateMachineConfigDto(
                config.getId(),
                config.getEntityType().name(),
                // process_type nullable: карта шаблона (entity_type = TEMPLATE) не привязана к типу
                // процесса — в V25 он равен null. Прямой .name() давал NPE и 500 на чтении карт.
                config.getProcessType() == null ? null : config.getProcessType().name(),
                config.getVersion(),
                config.getStates().stream()
                        .map(s -> new StateConfigDto(s.getId(), s.getCode()))
                        .toList(),
                config.getTransitions().stream()
                        .map(t -> new TransitionConfigDto(
                                t.getId(),
                                t.getCode(),
                                t.getFromState(),
                                t.getToState(),
                                t.getTrigger().name(),
                                t.getGuards(),
                                t.getActions(),
                                t.getEmits()
                        ))
                        .toList()
        );
    }
}
