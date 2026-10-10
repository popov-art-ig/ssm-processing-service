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

/**
 * Реестры guards и actions — {@code 07_api_contract.md} §24 (группа 15 «Реестр guards и actions»),
 * префикс {@code /api/v1/admin/registry}.
 *
 * <p>Только чтение: клиент получает плоские списки кодов и бинов, чтобы конструировать карты
 * переходов и видеть, что вообще зарегистрировано ({@code 05_guards_actions_registry.md} §3).
 * Источник — таблицы {@code guard_registry}/{@code action_registry}, то есть данные, а не
 * сканирование classpath (ADR-002, ADR-015).
 *
 * <p>Эндпоинтов {@code GET /registry/guards/{code}} и {@code /registry/actions/{code}}
 * (§24.2, §24.4) нет.
 */
@RestController
@RequestMapping("/api/v1/admin/registry")
@RequiredArgsConstructor
@Tag(name = "Registry", description = "API для доступа к реестрам Guards и Actions")
public class GuardActionRegistryController {

    private final GuardRegistryRepository guardRegistryRepository;
    private final ActionRegistryRepository actionRegistryRepository;

    /**
     * Список всех guards реестра.
     *
     * <p>Отвечает {@code 200} всегда: доменных ошибок у чтения справочника нет. Поле
     * {@code guardName} — это {@code code} записи (например {@code AggregationApproved}), а
     * {@code beanName} — имя бина-реализации, по которому {@code TransitionEngine} и находит
     * обработчик.
     */
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

    /**
     * Список всех actions реестра — та же форма, что у guards, но для действий перехода
     * ({@code 05_guards_actions_registry.md} §5).
     *
     * <p>Отвечает {@code 200} всегда. Клиенту список нужен для конструирования карт: без него
     * нельзя проверить {@code ACTION_NOT_FOUND} до публикации конфигурации
     * ({@code 05_guards_actions_registry.md} §7).
     */
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
