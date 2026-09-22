package ru.coordination.approval.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.coordination.approval.api.dto.registry.ActionRegistryDto;
import ru.coordination.approval.api.dto.registry.GuardRegistryDto;
import ru.coordination.approval.domain.registry.ActionRegistryEntry;
import ru.coordination.approval.domain.registry.GuardRegistryEntry;
import ru.coordination.approval.engine.registry.ActionRegistryRepository;
import ru.coordination.approval.engine.registry.GuardRegistryRepository;

@RestController
@RequestMapping("/api/v1/admin/registry")
@RequiredArgsConstructor
@Tag(name = "Registry", description = "API для доступа к реестрам Guards и Actions")
public class GuardActionRegistryController {

    private final GuardRegistryRepository guardRegistryRepository;
    private final ActionRegistryRepository actionRegistryRepository;

    @GetMapping("/guards")
    @Operation(summary = "Получить список Guards",
               description = "Возвращает список всех зарегистрированных Guards для использования в конфигурации переходов")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Список Guards")
    })
    public ResponseEntity<List<GuardRegistryDto>> listGuards() {
        List<GuardRegistryEntry> guards = guardRegistryRepository.findAll();
        return ResponseEntity.ok(guards.stream()
                .map(g -> new GuardRegistryDto(g.getCode(), g.getHandler(), g.getDescription()))
                .toList());
    }

    @GetMapping("/actions")
    @Operation(summary = "Получить список Actions",
               description = "Возвращает список всех зарегистрированных Actions для использования в конфигурации переходов")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Список Actions")
    })
    public ResponseEntity<List<ActionRegistryDto>> listActions() {
        List<ActionRegistryEntry> actions = actionRegistryRepository.findAll();
        return ResponseEntity.ok(actions.stream()
                .map(a -> new ActionRegistryDto(a.getCode(), a.getHandler(), a.getDescription()))
                .toList());
    }
}
