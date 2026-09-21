# PHASE-21-25 — REST API для UI (единый тикет)

> **Статус:** Не начато  
> **Story Points:** 27 (объединённые PHASE-21 до PHASE-25)  
> **Приоритет:** 🔴 Критический путь до MVP с UI

## Контекст

После PHASE-14 и PHASE-15 у нас есть полная domain-логика: процессы создаются из шаблонов, участники принимают решения, этапы закрываются, возврат на доработку работает, замечания обрабатываются.

**Но нет REST API** — всё работает только на уровне сервисов в Java-коде.

Эта **объединённая фаза** реализует **полный REST API для UI** — все эндпоинты для создания процессов, принятия решений, работы с замечаниями, управления шаблонами и агрегированные эндпоинты для оптимизации фронтенда.

После этой фазы можно:
- 🎨 **Начать разработку UI** — все API готовы
- 🧪 **Провести нагрузочное тестирование** — API доступны через HTTP
- 📬 **Тестировать через Postman** — создать коллекцию с полными сценариями

**Зависимости:**
- **Требует:** PHASE-15 (генерация из шаблонов), PHASE-08 (возврат), PHASE-11 (замечания), PHASE-14 (шаблоны)
- **Разблокирует:** Разработку UI, интеграционное тестирование, демо для стейкхолдеров

## Источники (для истории, не для перехода)

- `07_api_contract.md` — полная спецификация REST API (все группы эндпоинтов)
- `10_architecture.md` §8 (REST Controllers, DTO mapping)

## Объём фазы (Scope)

Этот единый тикет объединяет 5 оригинальных фаз:
- **PHASE-21:** REST API — группа «Процессы» (8 SP)
- **PHASE-22:** REST API — группа «Решения» (3 SP)
- **PHASE-23:** REST API — группа «Замечания и комментарии» (3 SP)
- **PHASE-24:** REST API — группа «Шаблоны и администрирование» (5 SP, **без зависимости от PHASE-17/18**)
- **PHASE-25:** REST API — агрегированные эндпоинты (5 SP)

**Новая фаза PHASE-29** (не входит сюда): REST API для настроек уведомлений и автоархивации (зависит от PHASE-17, PHASE-18)

### Что входит

#### 1. ProcessController — управление процессами (группа 12 API)

**Эндпоинты:**

```java
@RestController
@RequestMapping("/api/v1/processes")
@RequiredArgsConstructor
public class ProcessController {
    
    /**
     * Создать процесс из снимка сущности.
     * Шаблон подбирается автоматически по ApplicabilityRule.
     */
    @PostMapping
    public ResponseEntity<ProcessDto> createProcess(@RequestBody CreateProcessRequest request) {
        // 1. Создать EntitySnapshot из request
        // 2. MatchService.findMatchingTemplates()
        // 3. RouteGeneratorService.generateRoute()
        // 4. Маппинг в ProcessDto
    }
    
    /**
     * Получить процесс по ID.
     */
    @GetMapping("/{processId}")
    public ResponseEntity<ProcessDto> getProcess(@PathVariable UUID processId) {
        // ProcessService.getById() → ProcessDto
    }
    
    /**
     * Запустить процесс (Draft → InProgress).
     */
    @PostMapping("/{processId}/start")
    public ResponseEntity<ProcessDto> startProcess(
        @PathVariable UUID processId,
        @RequestBody StartProcessRequest request
    ) {
        // ProcessService.startProcess(processId, request.actorId())
    }
    
    /**
     * Отозвать процесс (InProgress/OnRework → Recalled).
     */
    @PostMapping("/{processId}/recall")
    public ResponseEntity<ProcessDto> recallProcess(
        @PathVariable UUID processId,
        @RequestBody RecallProcessRequest request
    ) {
        // ProcessService.recall(processId, request.actorId())
    }
    
    /**
     * Возобновить процесс после доработки (OnRework → InProgress).
     */
    @PostMapping("/{processId}/resume")
    public ResponseEntity<ProcessDto> resumeProcess(
        @PathVariable UUID processId,
        @RequestBody ResumeProcessRequest request
    ) {
        // ProcessService.resume(processId, request.targetStageId(), request.actorId())
    }
    
    /**
     * Список процессов с фильтрацией и пагинацией.
     */
    @GetMapping
    public ResponseEntity<Page<ProcessDto>> listProcesses(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID initiatorId,
        @RequestParam(required = false) String entityType,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        // ProcessRepository с фильтрами + PageRequest
    }
}
```

**DTOs:**

```java
public record CreateProcessRequest(
    String entityType,
    String entitySubtype,
    UUID entityId,
    Map<String, Object> attributes,
    UUID initiatorId
) {}

public record StartProcessRequest(UUID actorId) {}
public record RecallProcessRequest(UUID actorId) {}
public record ResumeProcessRequest(UUID targetStageId, UUID actorId) {}

public record ProcessDto(
    UUID id,
    String entityType,
    String entitySubtype,
    UUID entityId,
    String processType,
    String status,
    UUID initiatorId,
    UUID responsibleUserId,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt,
    List<StageDto> stages
) {}

public record StageDto(
    UUID id,
    Integer orderIdx,
    String name,
    String stageType,
    String status,
    Instant dueDate,
    Integer duration,
    String decisionMode,
    List<IterationDto> iterations
) {}

public record IterationDto(
    UUID id,
    Integer iterationIdx,
    String status,
    Instant dueDate,
    List<ParticipantDto> participants
) {}

public record ParticipantDto(
    UUID id,
    Integer orderIdx,
    UUID userId,
    UUID organizationId,
    String status,
    Instant dueDate,
    Boolean hasDecision
) {}
```

#### 2. DecisionController — принятие решений (группа 16 API)

**Эндпоинты:**

```java
@RestController
@RequestMapping("/api/v1/processes/{processId}/stages/{stageId}/participants/{participantId}")
@RequiredArgsConstructor
public class DecisionController {
    
    /**
     * Принять решение участником.
     */
    @PostMapping("/decide")
    public ResponseEntity<DecisionDto> decide(
        @PathVariable UUID processId,
        @PathVariable UUID stageId,
        @PathVariable UUID participantId,
        @RequestBody DecideRequest request
    ) {
        // DecisionService.decide(processId, stageId, participantId, request.decision())
    }
    
    /**
     * Получить решение участника.
     */
    @GetMapping("/decision")
    public ResponseEntity<DecisionDto> getDecision(
        @PathVariable UUID participantId
    ) {
        // DecisionRepository.findByParticipantId()
    }
    
    /**
     * Список всех решений по процессу.
     */
    @GetMapping("/api/v1/processes/{processId}/decisions")
    public ResponseEntity<List<DecisionDto>> listDecisions(
        @PathVariable UUID processId
    ) {
        // DecisionRepository.findByProcessId()
    }
}
```

**DTOs:**

```java
public record DecideRequest(
    String decision, // APPROVE, APPROVE_WITH_COMMENTS, REJECT
    String comment,
    UUID actorId
) {}

public record DecisionDto(
    UUID id,
    UUID participantId,
    String decision,
    String comment,
    Instant decidedAt,
    UUID decidedBy
) {}
```

#### 3. RemarkController — управление замечаниями (группа 17 API)

**Эндпоинты:**

```java
@RestController
@RequestMapping("/api/v1/processes/{processId}/remarks")
@RequiredArgsConstructor
public class RemarkController {
    
    /**
     * Создать замечание.
     */
    @PostMapping
    public ResponseEntity<RemarkDto> createRemark(
        @PathVariable UUID processId,
        @RequestBody CreateRemarkRequest request
    ) {
        // RemarkService.createRemark()
    }
    
    /**
     * Исправить замечание (Proposed → Resolved).
     */
    @PutMapping("/{remarkId}/resolve")
    public ResponseEntity<RemarkDto> resolveRemark(
        @PathVariable UUID remarkId,
        @RequestBody ResolveRemarkRequest request
    ) {
        // RemarkService.resolve()
    }
    
    /**
     * Отклонить замечание (Proposed → Rejected).
     */
    @PutMapping("/{remarkId}/reject")
    public ResponseEntity<RemarkDto> rejectRemark(
        @PathVariable UUID remarkId,
        @RequestBody RejectRemarkRequest request
    ) {
        // RemarkService.reject()
    }
    
    /**
     * Принять исправление (Resolved → Accepted).
     */
    @PutMapping("/{remarkId}/accept")
    public ResponseEntity<RemarkDto> acceptRemark(
        @PathVariable UUID remarkId,
        @RequestBody AcceptRemarkRequest request
    ) {
        // RemarkService.accept()
    }
    
    /**
     * Отклонить исправление (Resolved → Proposed).
     */
    @PutMapping("/{remarkId}/reject-resolution")
    public ResponseEntity<RemarkDto> rejectResolution(
        @PathVariable UUID remarkId,
        @RequestBody RejectResolutionRequest request
    ) {
        // RemarkService.rejectResolution()
    }
    
    /**
     * Список замечаний по процессу.
     */
    @GetMapping
    public ResponseEntity<List<RemarkDto>> listRemarks(
        @PathVariable UUID processId,
        @RequestParam(required = false) String status
    ) {
        // RemarkRepository.findByProcessId() с фильтром
    }
}
```

**DTOs:**

```java
public record CreateRemarkRequest(
    UUID stageId,
    String text,
    String severity, // CRITICAL, MAJOR, MINOR
    UUID authorId
) {}

public record ResolveRemarkRequest(
    String resolutionText,
    UUID actorId
) {}

public record RejectRemarkRequest(
    String reason,
    UUID actorId
) {}

public record AcceptRemarkRequest(UUID actorId) {}
public record RejectResolutionRequest(String reason, UUID actorId) {}

public record RemarkDto(
    UUID id,
    UUID processId,
    UUID stageId,
    String text,
    String severity,
    String status,
    UUID authorId,
    Instant createdAt,
    String resolutionText,
    Instant resolvedAt
) {}
```

#### 4. CommentController — комментарии (группа 18 API)

**Эндпоинты:**

```java
@RestController
@RequestMapping("/api/v1/processes/{processId}")
@RequiredArgsConstructor
public class CommentController {
    
    /**
     * Создать комментарий.
     */
    @PostMapping("/comments")
    public ResponseEntity<CommentDto> createComment(
        @PathVariable UUID processId,
        @RequestBody CreateCommentRequest request
    ) {
        // CommentService.createComment()
    }
    
    /**
     * Список комментариев.
     */
    @GetMapping("/comments")
    public ResponseEntity<List<CommentDto>> listComments(
        @PathVariable UUID processId,
        @RequestParam(required = false) UUID stageId,
        @RequestParam(required = false) UUID remarkId
    ) {
        // CommentRepository с фильтрами
    }
}
```

**DTOs:**

```java
public record CreateCommentRequest(
    UUID stageId,
    UUID remarkId,
    String text,
    UUID authorId
) {}

public record CommentDto(
    UUID id,
    UUID processId,
    UUID stageId,
    UUID remarkId,
    String text,
    UUID authorId,
    Instant createdAt
) {}
```

#### 5. TemplateController — управление шаблонами (группа 10 API)

**Эндпоинты:**

```java
@RestController
@RequestMapping("/api/v1/admin/templates")
@RequiredArgsConstructor
public class TemplateController {
    
    /**
     * Создать шаблон.
     */
    @PostMapping
    public ResponseEntity<TemplateDto> createTemplate(
        @RequestBody CreateTemplateRequest request
    ) {
        // TemplateService.create()
    }
    
    /**
     * Обновить шаблон (создаёт новую версию если Published).
     */
    @PutMapping("/{templateId}")
    public ResponseEntity<TemplateDto> updateTemplate(
        @PathVariable UUID templateId,
        @RequestBody UpdateTemplateRequest request
    ) {
        // TemplateService.update()
    }
    
    /**
     * Опубликовать шаблон (Draft → Published).
     */
    @PostMapping("/{templateId}/publish")
    public ResponseEntity<TemplateDto> publishTemplate(
        @PathVariable UUID templateId,
        @RequestBody PublishTemplateRequest request
    ) {
        // TemplateService.publish()
    }
    
    /**
     * Снять с публикации (Published → Deprecated).
     */
    @PostMapping("/{templateId}/deprecate")
    public ResponseEntity<TemplateDto> deprecateTemplate(
        @PathVariable UUID templateId,
        @RequestBody DeprecateTemplateRequest request
    ) {
        // TemplateService.deprecate()
    }
    
    /**
     * Архивировать (Deprecated → Archived).
     */
    @PostMapping("/{templateId}/archive")
    public ResponseEntity<TemplateDto> archiveTemplate(
        @PathVariable UUID templateId,
        @RequestBody ArchiveTemplateRequest request
    ) {
        // TemplateService.archive()
    }
    
    /**
     * Получить шаблон.
     */
    @GetMapping("/{templateId}")
    public ResponseEntity<TemplateDto> getTemplate(@PathVariable UUID templateId) {
        // TemplateService.getById()
    }
    
    /**
     * Список шаблонов.
     */
    @GetMapping
    public ResponseEntity<List<TemplateDto>> listTemplates(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String name
    ) {
        // TemplateRepository с фильтрами
    }
}
```

**DTOs:**

```java
public record CreateTemplateRequest(
    String name,
    String processType,
    Boolean processIterationEnabled,
    List<UUID> responsibleRoles,
    Boolean requiresResponsibleApproval,
    List<StageTemplateDto> stages,
    List<ApplicabilityRuleDto> applicabilityRules,
    UUID createdBy
) {}

public record UpdateTemplateRequest(
    String name,
    Boolean processIterationEnabled,
    List<UUID> responsibleRoles,
    Boolean requiresResponsibleApproval,
    List<StageTemplateDto> stages,
    List<ApplicabilityRuleDto> applicabilityRules
) {}

public record PublishTemplateRequest(UUID actorId) {}
public record DeprecateTemplateRequest(UUID actorId) {}
public record ArchiveTemplateRequest(UUID actorId) {}

public record TemplateDto(
    UUID id,
    String name,
    String processType,
    String status,
    Integer version,
    UUID parentTemplateId,
    Boolean processIterationEnabled,
    Instant publishedAt,
    List<StageTemplateDto> stages,
    List<ApplicabilityRuleDto> applicabilityRules
) {}

public record StageTemplateDto(
    UUID id,
    Integer orderIdx,
    String name,
    String description,
    String stageType,
    Integer duration,
    String decisionMode,
    String executionOrder,
    Boolean isMandatory,
    Boolean isOrderMandatory,
    List<Integer> allowedReturnStages,
    List<SlotTemplateDto> actorSlots
) {}

public record SlotTemplateDto(
    UUID id,
    Integer orderIdx,
    UUID userId,
    UUID organizationId,
    List<UUID> acceptableRoles,
    Boolean required,
    Boolean isUserEditable,
    Boolean isOrganizationEditable,
    Boolean isDeletable
) {}

public record ApplicabilityRuleDto(
    UUID id,
    Integer ruleIdx,
    List<String> entityTypes,
    List<String> entitySubtypes,
    Map<String, Object> attributeConditions
) {}
```

#### 6. StateMachineConfigController — конфигурация state machine (группа 23 API)

**Эндпоинты:**

```java
@RestController
@RequestMapping("/api/v1/admin/state-machines")
@RequiredArgsConstructor
public class StateMachineConfigController {
    
    /**
     * Список всех конфигураций.
     */
    @GetMapping
    public ResponseEntity<List<StateMachineConfigDto>> listConfigs() {
        // StateMachineConfigRepository.findAll()
    }
    
    /**
     * Получить конфигурацию.
     */
    @GetMapping("/{configId}")
    public ResponseEntity<StateMachineConfigDto> getConfig(@PathVariable UUID configId) {
        // StateMachineConfigRepository.findById()
    }
    
    /**
     * Создать новую версию конфигурации.
     */
    @PostMapping
    public ResponseEntity<StateMachineConfigDto> createConfig(
        @RequestBody CreateStateMachineConfigRequest request
    ) {
        // StateMachineConfigService.create()
    }
}
```

**DTOs:**

```java
public record StateMachineConfigDto(
    UUID id,
    String entityType,
    String processType,
    Integer version,
    List<StateConfigDto> states,
    List<TransitionConfigDto> transitions
) {}

public record StateConfigDto(
    UUID id,
    String stateName
) {}

public record TransitionConfigDto(
    UUID id,
    String transitionName,
    String fromState,
    String toState,
    String triggerType,
    List<String> guards,
    List<String> actions,
    List<String> emittedEvents
) {}
```

#### 7. GuardActionRegistryController — реестры (группа 24 API)

**Эндпоинты:**

```java
@RestController
@RequestMapping("/api/v1/admin/registry")
@RequiredArgsConstructor
public class GuardActionRegistryController {
    
    /**
     * Список всех guards.
     */
    @GetMapping("/guards")
    public ResponseEntity<List<GuardRegistryDto>> listGuards() {
        // GuardRegistryRepository.findAll()
    }
    
    /**
     * Список всех actions.
     */
    @GetMapping("/actions")
    public ResponseEntity<List<ActionRegistryDto>> listActions() {
        // ActionRegistryRepository.findAll()
    }
}
```

**DTOs:**

```java
public record GuardRegistryDto(
    String guardName,
    String beanName,
    String description
) {}

public record ActionRegistryDto(
    String actionName,
    String beanName,
    String description
) {}
```

#### 8. AdminController — администрирование (группа 22 API, **без PHASE-17/18**)

**Эндпоинты (только восстановление из архива):**

```java
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    
    /**
     * Восстановить процесс из архива.
     * (Archived → предыдущий статус)
     */
    @PostMapping("/processes/{processId}/restore")
    public ResponseEntity<ProcessDto> restoreProcess(
        @PathVariable UUID processId,
        @RequestBody RestoreProcessRequest request
    ) {
        // AdminService.restoreFromArchive()
    }
}
```

**DTOs:**

```java
public record RestoreProcessRequest(UUID actorId) {}
```

**Не входит (перенесено в PHASE-29):**
- ❌ `GET /admin/notification-settings` (зависит от PHASE-17)
- ❌ `PUT /admin/notification-settings` (зависит от PHASE-17)
- ❌ Эндпоинты для управления автоархивацией (зависит от PHASE-18)

#### 9. ApprovalViewController — агрегированные эндпоинты (группа 11 API)

**Эндпоинты:**

```java
@RestController
@RequestMapping("/api/v1/approval-view")
@RequiredArgsConstructor
public class ApprovalViewController {
    
    /**
     * Полная информация о процессе для UI (один запрос).
     * Возвращает: процесс + этапы + участники + решения + замечания.
     */
    @GetMapping("/entities/{entityId}")
    public ResponseEntity<ApprovalViewDto> getApprovalView(
        @PathVariable UUID entityId,
        @RequestParam String entityType
    ) {
        // 1. Найти ProcessInstance по entityType + entityId
        // 2. Загрузить все связанные данные (stages, iterations, participants, decisions, remarks)
        // 3. Агрегировать в ApprovalViewDto
    }
    
    /**
     * Задачи текущего пользователя (участники в статусе Assigned).
     */
    @GetMapping("/tasks/my")
    public ResponseEntity<List<TaskDto>> getMyTasks(
        @RequestParam UUID userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        // ParticipantRepository.findByUserIdAndStatus("Assigned") + пагинация
    }
    
    /**
     * Процессы текущего пользователя (как инициатор или ответственный).
     */
    @GetMapping("/processes/my")
    public ResponseEntity<Page<ProcessSummaryDto>> getMyProcesses(
        @RequestParam UUID userId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        // ProcessRepository.findByInitiatorIdOrResponsibleUserId() + пагинация
    }
}
```

**DTOs:**

```java
public record ApprovalViewDto(
    ProcessDto process,
    List<StageWithDecisionsDto> stages,
    List<RemarkDto> remarks,
    List<CommentDto> comments
) {}

public record StageWithDecisionsDto(
    StageDto stage,
    List<ParticipantWithDecisionDto> participants
) {}

public record ParticipantWithDecisionDto(
    ParticipantDto participant,
    DecisionDto decision // может быть null
) {}

public record TaskDto(
    UUID taskId, // participant.id
    UUID processId,
    UUID stageId,
    UUID participantId,
    String processEntityType,
    UUID processEntityId,
    String stageName,
    Instant dueDate,
    String status
) {}

public record ProcessSummaryDto(
    UUID id,
    String entityType,
    UUID entityId,
    String status,
    Instant createdAt,
    Instant dueDate,
    String currentStageName
) {}
```

### 10. Общие компоненты

#### Exception Handling

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("BAD_REQUEST", ex.getMessage()));
    }
    
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("CONFLICT", ex.getMessage()));
    }
    
    // ... другие исключения
}

public record ErrorResponse(String code, String message) {}
```

#### Validation

```java
// В DTOs добавить валидацию
public record CreateProcessRequest(
    @NotBlank String entityType,
    @NotNull UUID entityId,
    @NotNull UUID initiatorId,
    @NotNull Map<String, Object> attributes
) {}
```

#### Mappers

```java
@Component
public class ProcessMapper {
    public ProcessDto toDto(ProcessInstance process) {
        return new ProcessDto(
            process.getId(),
            process.getEntityType(),
            process.getEntitySubtype(),
            process.getEntityId(),
            process.getProcessType().name(),
            process.getStatus(),
            process.getInitiatorId(),
            process.getResponsibleUserId(),
            process.getCreatedAt(),
            process.getStartedAt(),
            process.getCompletedAt(),
            process.getStages().stream()
                .map(this::toStageDto)
                .toList()
        );
    }
    
    private StageDto toStageDto(StageInstance stage) {
        // Маппинг StageInstance → StageDto
    }
}
```

## Явно не входит (Out of scope)

- ❌ **WebSocket для real-time обновлений** → future enhancement
- ❌ **GraphQL API** → future enhancement
- ❌ **Batch операции** (массовое создание процессов) → future enhancement
- ❌ **Экспорт в PDF/Excel** → future enhancement
- ❌ **Настройки уведомлений (NotificationSettings API)** → PHASE-29 (зависит от PHASE-17)
- ❌ **Управление автоархивацией** → PHASE-29 (зависит от PHASE-18)
- ❌ **Swagger/OpenAPI спецификация** — можно добавить через springdoc-openapi, но не обязательно для MVP

## Что уже есть в репозитории на момент постановки

1. **Domain-сервисы** (PHASE-03, 05-08, 11, 14-15):
   - `ProcessService`, `DecisionService`, `RemarkService`, `TemplateService`
   - `MatchService`, `RouteGeneratorService`
   - Все работают, протестированы

2. **JPA-сущности и репозитории** (PHASE-01)

3. **TransitionEngine** (PHASE-02)

4. **Spring Boot** настроен, application.yml готов

## Технические требования

### 1. Spring Web MVC

**Конфигурация:**

```yaml
# application.yml
server:
  port: 8080
  servlet:
    context-path: /

spring:
  jackson:
    serialization:
      write-dates-as-timestamps: false
    deserialization:
      fail-on-unknown-properties: false
```

### 2. CORS (для локальной разработки UI)

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:3000", "http://localhost:4200")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true);
    }
}
```

### 3. Пагинация

```java
// Использовать Spring Data Pageable
@GetMapping
public ResponseEntity<Page<ProcessDto>> listProcesses(
    Pageable pageable // автоматически парсит ?page=0&size=20&sort=createdAt,desc
) {
    Page<ProcessInstance> processes = processRepository.findAll(pageable);
    return ResponseEntity.ok(processes.map(processMapper::toDto));
}
```

### 4. Фильтрация

```java
// Через Specification для сложных фильтров
public interface ProcessRepository extends JpaRepository<ProcessInstance, UUID>, 
                                            JpaSpecificationExecutor<ProcessInstance> {
}

// В сервисе
Specification<ProcessInstance> spec = Specification.where(null);
if (status != null) {
    spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
}
if (initiatorId != null) {
    spec = spec.and((root, query, cb) -> cb.equal(root.get("initiatorId"), initiatorId));
}
Page<ProcessInstance> result = processRepository.findAll(spec, pageable);
```

### 5. HTTP-коды ответов

| Код | Случай |
|-----|--------|
| 200 OK | GET успешно, PUT успешно |
| 201 Created | POST успешно |
| 204 No Content | DELETE успешно |
| 400 Bad Request | Невалидные данные, IllegalArgumentException |
| 404 Not Found | EntityNotFoundException |
| 409 Conflict | IllegalStateException (например, процесс уже запущен) |
| 500 Internal Server Error | Неожиданная ошибка |

### 6. Производительность агрегированных эндпоинтов

**Требование:** `GET /approval-view/entities/{entityId}` должен выполняться **< 500ms** для процесса с 10 этапами.

**Оптимизация:**

```java
@Service
public class ApprovalViewService {
    
    @Transactional(readOnly = true)
    public ApprovalViewDto getApprovalView(String entityType, UUID entityId) {
        // 1. Один запрос для ProcessInstance с Fetch Join
        ProcessInstance process = processRepository
            .findByEntityTypeAndEntityIdWithStagesAndIterations(entityType, entityId)
            .orElseThrow(() -> new EntityNotFoundException("Process not found"));
        
        // 2. Batch-загрузка Participant + Decision (один запрос)
        List<UUID> iterationIds = process.getStages().stream()
            .flatMap(s -> s.getIterations().stream())
            .map(StageIteration::getId)
            .toList();
        
        List<Participant> participants = participantRepository.findByStageIterationIdIn(iterationIds);
        List<Decision> decisions = decisionRepository.findByParticipantIdIn(
            participants.stream().map(Participant::getId).toList()
        );
        
        // 3. Batch-загрузка Remark + Comment (один запрос)
        List<Remark> remarks = remarkRepository.findByProcessId(process.getId());
        List<Comment> comments = commentRepository.findByProcessId(process.getId());
        
        // 4. Агрегация в DTO
        return buildApprovalViewDto(process, participants, decisions, remarks, comments);
    }
}
```

**Fetch Join query:**

```java
@Query("SELECT p FROM ProcessInstance p " +
       "LEFT JOIN FETCH p.stages s " +
       "LEFT JOIN FETCH s.iterations i " +
       "WHERE p.entityType = :entityType AND p.entityId = :entityId")
Optional<ProcessInstance> findByEntityTypeAndEntityIdWithStagesAndIterations(
    @Param("entityType") String entityType, 
    @Param("entityId") UUID entityId
);
```

## Критерии приёмки

### ProcessController

✅ `POST /processes` создаёт процесс из entitySnapshot  
✅ `POST /processes/{id}/start` запускает процесс (Draft → InProgress)  
✅ `POST /processes/{id}/recall` отзывает процесс  
✅ `POST /processes/{id}/resume` возобновляет процесс с выбором targetStageId  
✅ `GET /processes` возвращает список с пагинацией и фильтрами  
✅ `GET /processes/{id}` возвращает ProcessDto со всеми stages/iterations/participants  

### DecisionController

✅ `POST .../decide` принимает решение → Decision создан, Participant.status = Decided  
✅ `GET .../decision` возвращает DecisionDto  
✅ `GET /processes/{id}/decisions` возвращает список всех решений  

### RemarkController

✅ Полный lifecycle замечания через API: create → resolve → accept/reject  
✅ `GET /processes/{id}/remarks` возвращает список с фильтром по status  

### CommentController

✅ `POST .../comments` создаёт комментарий  
✅ `GET .../comments` возвращает список с фильтрами (stageId, remarkId)  

### TemplateController

✅ CRUD операции с шаблонами работают  
✅ `POST .../publish` публикует шаблон (валидация проходит)  
✅ `POST .../deprecate`, `POST .../archive` работают  
✅ `GET /templates` возвращает список с фильтрами  

### StateMachineConfigController, GuardActionRegistryController

✅ Можно получить список всех конфигураций state machine  
✅ Можно получить список guards/actions из реестров  

### AdminController

✅ `POST .../restore` восстанавливает процесс из архива  

### ApprovalViewController

✅ `GET /approval-view/entities/{entityId}` возвращает полную информацию за **< 500ms**  
✅ `GET /tasks/my` возвращает задачи пользователя с пагинацией  
✅ `GET /processes/my` возвращает процессы пользователя  

### Общие требования

✅ Все эндпоинты возвращают правильные HTTP-коды  
✅ Валидация запросов работает (400 Bad Request для невалидных данных)  
✅ Exception handling: 404 для EntityNotFoundException, 409 для IllegalStateException  
✅ CORS настроен для локальной разработки  
✅ Пагинация работает через Pageable  
✅ Фильтрация работает для списковых эндпоинтов  

## Тестирование

### Юнит-тесты (MockMvc)

**ProcessControllerTest:**

```java
@WebMvcTest(ProcessController.class)
class ProcessControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private ProcessService processService;
    
    @MockBean
    private MatchService matchService;
    
    @MockBean
    private RouteGeneratorService routeGeneratorService;
    
    @Test
    void createProcess_success() throws Exception {
        // Given
        CreateProcessRequest request = new CreateProcessRequest(
            "CONTRACT", null, UUID.randomUUID(),
            Map.of("amount", 1500000),
            UUID.randomUUID()
        );
        
        Template mockTemplate = createMockTemplate();
        ProcessInstance mockProcess = createMockProcess();
        
        when(matchService.findMatchingTemplates(any())).thenReturn(List.of(mockTemplate));
        when(matchService.selectBestTemplate(any())).thenReturn(mockTemplate);
        when(routeGeneratorService.generateRoute(any(), any(), any())).thenReturn(mockProcess);
        
        // When & Then
        mockMvc.perform(post("/api/v1/processes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(mockProcess.getId().toString()))
            .andExpect(jsonPath("$.status").value("Draft"));
    }
    
    @Test
    void createProcess_noMatchingTemplate_returns404() throws Exception {
        // Given
        when(matchService.findMatchingTemplates(any())).thenReturn(List.of());
        
        // When & Then
        mockMvc.perform(post("/api/v1/processes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }
    
    @Test
    void startProcess_success() throws Exception {
        // Given
        UUID processId = UUID.randomUUID();
        StartProcessRequest request = new StartProcessRequest(UUID.randomUUID());
        
        ProcessInstance mockProcess = createMockProcess();
        mockProcess.setStatus("InProgress");
        
        when(processService.startProcess(eq(processId), any())).thenReturn(mockProcess);
        
        // When & Then
        mockMvc.perform(post("/api/v1/processes/" + processId + "/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("InProgress"));
    }
    
    @Test
    void listProcesses_withFilters_success() throws Exception {
        // Given
        Page<ProcessInstance> mockPage = new PageImpl<>(List.of(createMockProcess()));
        when(processRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(mockPage);
        
        // When & Then
        mockMvc.perform(get("/api/v1/processes")
                .param("status", "InProgress")
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").value(1));
    }
}
```

**DecisionControllerTest, RemarkControllerTest** — аналогично

### Интеграционные тесты (Testcontainers + реальный HTTP)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProcessApiIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("approval_test")
        .withUsername("test")
        .withPassword("test");
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private TemplateService templateService;
    
    @Test
    void fullCycle_createProcessFromTemplate_startProcess_decideParticipants_completeProcess() {
        // 1. Создать и опубликовать шаблон
        Template template = templateService.create(createTemplateRequest());
        templateService.publish(template.getId(), adminId);
        
        // 2. Создать процесс через API
        CreateProcessRequest request = new CreateProcessRequest(
            "CONTRACT", null, UUID.randomUUID(),
            Map.of("amount", 1500000),
            initiatorId
        );
        
        ResponseEntity<ProcessDto> createResponse = restTemplate.postForEntity(
            "/api/v1/processes",
            request,
            ProcessDto.class
        );
        
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ProcessDto process = createResponse.getBody();
        assertThat(process.status()).isEqualTo("Draft");
        
        // 3. Запустить процесс
        StartProcessRequest startRequest = new StartProcessRequest(initiatorId);
        ResponseEntity<ProcessDto> startResponse = restTemplate.postForEntity(
            "/api/v1/processes/" + process.id() + "/start",
            startRequest,
            ProcessDto.class
        );
        
        assertThat(startResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(startResponse.getBody().status()).isEqualTo("InProgress");
        
        // 4. Получить первый этап
        StageDto firstStage = startResponse.getBody().stages().get(0);
        assertThat(firstStage.status()).isEqualTo("Active");
        
        // 5. Получить первого участника
        ParticipantDto participant = firstStage.iterations().get(0).participants().get(0);
        assertThat(participant.status()).isEqualTo("Assigned");
        
        // 6. Принять решение
        DecideRequest decideRequest = new DecideRequest("APPROVE", "LGTM", participant.userId());
        ResponseEntity<DecisionDto> decideResponse = restTemplate.postForEntity(
            "/api/v1/processes/" + process.id() + 
                "/stages/" + firstStage.id() + 
                "/participants/" + participant.id() + "/decide",
            decideRequest,
            DecisionDto.class
        );
        
        assertThat(decideResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // 7. Проверить что этап закрылся (если все участники решили)
        ResponseEntity<ProcessDto> finalProcess = restTemplate.getForEntity(
            "/api/v1/processes/" + process.id(),
            ProcessDto.class
        );
        
        // ... дальнейшая проверка
    }
}
```

### Postman коллекция

Создать коллекцию `approval-api.postman_collection.json` с:

**Папка "1. Setup" (выполнить первым):**
- `POST /admin/templates` — создать тестовый шаблон
- `POST /admin/templates/{id}/publish` — опубликовать

**Папка "2. Happy Path":**
- `POST /processes` — создать процесс
- `POST /processes/{id}/start` — запустить
- `POST .../decide` — принять решение (для каждого участника)
- `GET /processes/{id}` — проверить завершение

**Папка "3. Rework Path":**
- `POST /processes` + start
- `POST .../decide` с REJECT
- `POST /processes/{id}/resume` — возобновить с выбором этапа

**Папка "4. Remarks":**
- `POST .../remarks` — создать замечание
- `PUT .../remarks/{id}/resolve` — исправить
- `PUT .../remarks/{id}/accept` — принять

**Папка "5. Aggregated Views":**
- `GET /approval-view/entities/{entityId}`
- `GET /tasks/my?userId={userId}`
- `GET /processes/my?userId={userId}`

**Environment variables:**
```json
{
  "baseUrl": "http://localhost:8080/api/v1",
  "adminId": "{{$guid}}",
  "initiatorId": "{{$guid}}",
  "processId": "", // заполняется из response
  "templateId": ""
}
```

## Открытые вопросы

1. **Аутентификация и авторизация:**
   - В этой фазе нет Spring Security — `actorId` передаётся в request body
   - Для production нужен JWT/OAuth2 → future enhancement
   - **Решение:** В DTO добавить `actorId`, в будущем заменить на `@AuthenticationPrincipal`

2. **Rate limiting:**
   - Нет защиты от DDoS
   - **Решение:** Можно добавить через Spring Cloud Gateway или nginx в production

3. **API versioning:**
   - Сейчас `/api/v1/...`
   - Если изменится контракт → `/api/v2/...`?
   - **Решение:** Пока v1 достаточно, версионирование через URL path

4. **Swagger/OpenAPI:**
   - Нужен ли для этой фазы?
   - **Решение:** Опционально через `springdoc-openapi-starter-webmvc-ui`, но не обязательно для MVP

5. **Что делать с PHASE-17/18 зависимостями?**
   - **Решение:** Создать PHASE-29 с эндпоинтами для настроек уведомлений и автоархивации
   - PHASE-29 реализуется после PHASE-17 и PHASE-18

## Ревью Cowork

*(Заполняется после реализации и проверки)*
