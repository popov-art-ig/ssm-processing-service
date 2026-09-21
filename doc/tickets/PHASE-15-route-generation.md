# PHASE-15 — Подбор и генерация маршрута (Route Matching & Generation)

> **Статус:** Не начато  
> **Story Points:** 21  
> **Приоритет:** 🔴 Критический путь до MVP

## Контекст

После PHASE-14 у нас есть управление шаблонами: создание, публикация, версионирование. Но процессы всё ещё нужно создавать программно.

**Эта фаза реализует генерацию процессов из шаблонов** — ключевую функциональность для продакшена. По снимку сущности (entitySnapshot) система:
1. Подбирает подходящий шаблон по правилам применимости (ApplicabilityRule)
2. Генерирует ProcessInstance со всей структурой (StageInstance, StageIteration, Participant)
3. Резолвит роли в конкретных пользователей через адаптер
4. Рассчитывает дедлайны
5. Валидирует результат

После этой фазы процессы можно создавать через REST API вызовом: `POST /processes {entitySnapshot}` → ProcessInstance готов к запуску.

**Зависимости:**
- **Требует:** PHASE-14 (шаблоны существуют и опубликованы)
- **Разблокирует:** PHASE-21 (REST API — создание процессов), production-ready MVP

## Источники (для истории, не для перехода)

- `03_domain_model.md` §6.5 (SlotTemplate), §6.6 (ApplicabilityRule)
- `08_db_schema.md` §6.4 (slot_template), §6.5 (applicability_rule)
- `09_adapters.md` §2 (RoleResolverAdapter), §1 (EntityAdapter)
- `10_architecture.md` §7.4 (MatchService), §7.5 (RouteGeneratorService)
- `11_adr.md` ADR-023 (Snapshot-on-Start для шаблонов)

## Объём фазы (Scope)

### Что входит

1. **MatchService** — подбор подходящего шаблона:
   - `findMatchingTemplates(EntitySnapshot)` — находит все шаблоны, чьи ApplicabilityRule матчатся
   - `selectBestTemplate(List<Template>)` — выбирает лучший по приоритету (ruleIdx)
   - Matching logic: проверка entityType, entitySubtype, attributeConditions

2. **RouteGeneratorService** — генерация ProcessInstance из шаблона:
   - `generateRoute(UUID templateId, EntitySnapshot entitySnapshot, UUID initiatorId)` → ProcessInstance
   - Создаёт ProcessInstance (статус Draft, Snapshot-on-Start)
   - Клонирует StageInstance[] из StageTemplate[]
   - Создаёт StageIteration для каждого этапа (iterationIdx=1)
   - Создаёт Participant[] из SlotTemplate[] с резолвингом ролей
   - Рассчитывает дедлайны (baseDate + duration рабочих дней)
   - Не запускает процесс (статус остаётся Draft)

3. **RoleResolverAdapter** (stub для этой фазы, реальная реализация в PHASE-20):
   - `resolveRole(UUID roleId, UUID organizationId, EntitySnapshot context)` → List<UUID> userIds
   - Stub возвращает пустой список (валидация отловит это для required слотов)

4. **EntityAdapter** (stub для этой фазы):
   - `fetchEntitySnapshot(String entityType, UUID entityId)` → EntitySnapshot
   - Stub возвращает минимальный объект для тестов

5. **RouteValidatorService** — валидация сгенерированного маршрута:
   - `validate(ProcessInstance)` — проверяет что все обязательные слоты заполнены
   - Проверяет что дедлайны корректны (следующий этап > предыдущего)
   - Проверяет что участники имеют userId (не null после резолвинга)

6. **Миграция V31** — нет новых state machine, но возможно добавление guards/actions если понадобятся

### Что входит: детализация алгоритмов

#### Matching алгоритм (MatchService)

Для каждого `ApplicabilityRule` шаблона:

1. **Проверка entityType:**
   ```java
   if (!rule.getEntityTypes().isEmpty() 
       && !rule.getEntityTypes().contains(entitySnapshot.getEntityType())) {
       return false; // не матчится
   }
   ```

2. **Проверка entitySubtype:**
   ```java
   if (!rule.getEntitySubtypes().isEmpty()
       && !rule.getEntitySubtypes().contains(entitySnapshot.getEntitySubtype())) {
       return false;
   }
   ```

3. **Проверка attributeConditions:**
   ```json
   // Пример attributeConditions:
   {
     "amount": { "gte": 1000000 },
     "priority": { "in": ["HIGH", "CRITICAL"] },
     "status": { "eq": "PENDING_APPROVAL" }
   }
   ```
   
   Поддерживаемые операторы:
   - `eq` — equals (точное совпадение)
   - `in` — in array (значение в списке)
   - `gte` — greater than or equal (для чисел)
   - `lte` — less than or equal
   - `gt` — greater than
   - `lt` — less than
   - `contains` — substring для строк

   ```java
   for (String attribute : attributeConditions.keySet()) {
       Object actualValue = entitySnapshot.getAttributes().get(attribute);
       Map<String, Object> predicates = attributeConditions.get(attribute);
       
       if (!evaluatePredicates(actualValue, predicates)) {
           return false;
       }
   }
   return true; // все условия выполнены
   ```

4. **Выбор лучшего шаблона:**
   - Если несколько шаблонов матчатся, выбирается первый по `ruleIdx` (меньший = выше приоритет)
   - Если ни один не матчится → `NoMatchingTemplateException`

#### Генерация маршрута (RouteGeneratorService)

**Входные данные:**
- `templateId` — конкретная версия Template
- `EntitySnapshot` — снимок сущности (entityType, entityId, attributes)
- `initiatorId` — инициатор процесса

**Шаги:**

1. **Создать ProcessInstance:**
   ```java
   ProcessInstance process = ProcessInstance.builder()
       .id(UUID.randomUUID())
       .entityType(entitySnapshot.getEntityType())
       .entitySubtype(entitySnapshot.getEntitySubtype())
       .entityId(entitySnapshot.getEntityId())
       .templateRef(templateId) // Snapshot-on-Start: фиксация версии шаблона
       .processType(template.getProcessType())
       .configVersion(/* найти latest version для ProcessStateMachine */)
       .status("Draft")
       .initiatorId(initiatorId)
       .createdAt(Instant.now())
       .version(0)
       .build();
   
   // Для UNIFIED:
   if (template.getProcessType() == ProcessType.UNIFIED) {
       // Резолвить responsibleRoles → responsibleUserId
       UUID responsibleUserId = resolveResponsible(template.getResponsibleRoles(), entitySnapshot);
       process.setResponsibleUserId(responsibleUserId);
       process.setResponsibleResolvedRoleRef(/* роль которая матчнулась */);
   }
   ```

2. **Создать StageInstance для каждого StageTemplate:**
   ```java
   Instant baseDate = Instant.now(); // или entitySnapshot.targetDate если есть
   
   for (StageTemplate stageTemplate : template.getStages()) {
       StageInstance stage = StageInstance.builder()
           .id(UUID.randomUUID())
           .processId(process.getId())
           .orderIdx(stageTemplate.getOrderIdx())
           .originalOrderIdx(stageTemplate.getOrderIdx())
           .name(stageTemplate.getName())
           .description(stageTemplate.getDescription())
           .stageType(stageTemplate.getStageType())
           .duration(stageTemplate.getDuration())
           .decisionMode(stageTemplate.getDecisionMode())
           .executionOrder(stageTemplate.getExecutionOrder())
           .isMandatory(stageTemplate.getIsMandatory())
           .isOrderMandatory(stageTemplate.getIsOrderMandatory())
           .allowedReturnStages(stageTemplate.getAllowedReturnStages())
           .status("Pending")
           .dueDate(calculateDueDate(baseDate, stageTemplate.getDuration()))
           .build();
       
       process.getStages().add(stage);
   }
   ```

3. **Создать StageIteration для каждого этапа:**
   ```java
   for (StageInstance stage : process.getStages()) {
       StageIteration iteration = StageIteration.builder()
           .id(UUID.randomUUID())
           .stageInstanceId(stage.getId())
           .iterationIdx(1) // первая итерация
           .status("Pending")
           .dueDate(stage.getDueDate())
           .build();
       
       stage.getIterations().add(iteration);
   }
   ```

4. **Создать Participant для каждого SlotTemplate:**
   ```java
   for (StageInstance stage : process.getStages()) {
       StageIteration iteration = stage.getIterations().get(0); // первая итерация
       List<SlotTemplate> slots = slotTemplateRepository.findByStageTemplateId(stage.getSourceTemplateId());
       
       for (SlotTemplate slot : slots) {
           UUID userId = resolveSlot(slot, entitySnapshot);
           
           if (slot.getRequired() && userId == null) {
               throw new RequiredSlotNotResolvedException(
                   "Cannot resolve required slot at stage " + stage.getOrderIdx());
           }
           
           Participant participant = Participant.builder()
               .id(UUID.randomUUID())
               .stageIterationId(iteration.getId())
               .orderIdx(slot.getOrderIdx())
               .userId(userId) // может быть null для необязательных слотов
               .organizationId(slot.getOrganizationId())
               .resolvedRoleRef(/* роль если резолвили через роли */)
               .status("Pending")
               .dueDate(iteration.getDueDate())
               .assignedBy(null) // заполнится при активации этапа
               .build();
           
           iteration.getParticipants().add(participant);
       }
   }
   ```

5. **Валидация результата:**
   ```java
   routeValidatorService.validate(process);
   ```

6. **Сохранить через ProcessRepository** (cascade persist для всех связей)

7. **Вернуть ProcessInstance**

#### Резолвинг слота (resolveSlot)

```java
private UUID resolveSlot(SlotTemplate slot, EntitySnapshot entitySnapshot) {
    // 1. Если userId явно задан → вернуть его
    if (slot.getUserId() != null) {
        return slot.getUserId();
    }
    
    // 2. Если acceptableRoles задан → резолвить через RoleResolverAdapter
    if (slot.getAcceptableRoles() != null && slot.getAcceptableRoles().length > 0) {
        for (UUID roleId : slot.getAcceptableRoles()) {
            List<UUID> candidates = roleResolverAdapter.resolveRole(
                roleId, 
                slot.getOrganizationId(), 
                entitySnapshot
            );
            
            if (!candidates.isEmpty()) {
                return candidates.get(0); // первый подходящий
            }
        }
    }
    
    // 3. Если ничего не резолвилось → null (валидация отловит это для required слотов)
    return null;
}
```

#### Резолвинг ответственного (UNIFIED)

```java
private UUID resolveResponsible(UUID[] responsibleRoles, EntitySnapshot entitySnapshot) {
    if (responsibleRoles == null || responsibleRoles.length == 0) {
        throw new IllegalArgumentException("UNIFIED template must have responsibleRoles");
    }
    
    for (UUID roleId : responsibleRoles) {
        List<UUID> candidates = roleResolverAdapter.resolveRole(roleId, null, entitySnapshot);
        if (!candidates.isEmpty()) {
            return candidates.get(0);
        }
    }
    
    throw new ResponsibleNotResolvedException("Cannot resolve responsible from roles: " + Arrays.toString(responsibleRoles));
}
```

#### Расчёт дедлайнов

```java
private Instant calculateDueDate(Instant baseDate, int durationDays) {
    // Простой вариант (без учёта выходных/праздников):
    return baseDate.plus(durationDays, ChronoUnit.DAYS);
    
    // Для production (PHASE-20) можно добавить WorkingDaysCalculator через адаптер
}
```

## Явно не входит (Out of scope)

- ❌ **Запуск процесса (StartProcess)** — уже реализован в PHASE-03, генерация только создаёт Draft
- ❌ **REST API** → PHASE-21 (эта фаза — domain logic)
- ❌ **Реальная реализация RoleResolverAdapter** → PHASE-20 (в этой фазе stub)
- ❌ **WorkingDaysCalculator** (учёт выходных/праздников) → PHASE-20
- ❌ **Валидация бизнес-правил сущности** (например, "договор должен быть в статусе PENDING") — это ответственность вызывающей системы
- ❌ **Кэширование шаблонов** — оптимизация для будущего
- ❌ **Batch-генерация множества процессов** — сейчас только по одному

## Что уже есть в репозитории на момент постановки

1. **Таблицы БД** (PHASE-01):
   - `template`, `stage_template`, `slot_template`, `applicability_rule`
   - `process_instance`, `stage_instance`, `stage_iteration`, `participant`

2. **JPA-сущности** (PHASE-01):
   - `Template`, `StageTemplate`, `SlotTemplate`, `ApplicabilityRule`
   - `ProcessInstance`, `StageInstance`, `StageIteration`, `Participant`

3. **TemplateService** (PHASE-14):
   - CRUD операции с шаблонами
   - `getById(templateId)` — будет использоваться для загрузки шаблона

4. **ProcessService** (PHASE-03):
   - `startProcess(processId, actorId)` — запуск после генерации

5. **ProcessRepository, StageRepository, ParticipantRepository** (PHASE-01)

## Технические требования

### 1. EntitySnapshot (DTO для передачи снимка сущности)

**Пакет:** `ru.coordination.approval.dto`

```java
public record EntitySnapshot(
    String entityType,
    String entitySubtype,
    UUID entityId,
    Map<String, Object> attributes, // атрибуты для matching
    Instant targetDate // опционально: базовая дата для расчёта дедлайнов
) {
    public EntitySnapshot {
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("entityType is required");
        }
        if (entityId == null) {
            throw new IllegalArgumentException("entityId is required");
        }
        attributes = attributes != null ? attributes : Map.of();
    }
}
```

### 2. MatchService

**Пакет:** `ru.coordination.approval.service`

```java
@Service
@RequiredArgsConstructor
public class MatchService {
    private final TemplateRepository templateRepository;
    private final ApplicabilityRuleRepository applicabilityRuleRepository;
    
    /**
     * Найти все шаблоны, которые матчатся с entitySnapshot.
     * 
     * @return список Template[], отсортированных по приоритету (ruleIdx asc)
     */
    public List<Template> findMatchingTemplates(EntitySnapshot entitySnapshot) {
        // 1. Получить все Published шаблоны
        List<Template> publishedTemplates = templateRepository.findByStatus(TemplateStatus.PUBLISHED);
        
        // 2. Для каждого шаблона проверить ApplicabilityRule[]
        List<TemplateWithPriority> matches = new ArrayList<>();
        
        for (Template template : publishedTemplates) {
            List<ApplicabilityRule> rules = applicabilityRuleRepository
                .findByTemplateIdOrderByRuleIdx(template.getId());
            
            for (ApplicabilityRule rule : rules) {
                if (ruleMatches(rule, entitySnapshot)) {
                    matches.add(new TemplateWithPriority(template, rule.getRuleIdx()));
                    break; // первое совпадение для шаблона достаточно
                }
            }
        }
        
        // 3. Сортировать по приоритету (меньший ruleIdx = выше приоритет)
        return matches.stream()
            .sorted(Comparator.comparing(TemplateWithPriority::priority))
            .map(TemplateWithPriority::template)
            .toList();
    }
    
    /**
     * Выбрать лучший шаблон из списка (первый = highest priority).
     */
    public Template selectBestTemplate(List<Template> matchingTemplates) {
        if (matchingTemplates.isEmpty()) {
            throw new NoMatchingTemplateException("No templates match the entity snapshot");
        }
        return matchingTemplates.get(0);
    }
    
    private boolean ruleMatches(ApplicabilityRule rule, EntitySnapshot snapshot) {
        // 1. Проверка entityTypes
        if (!rule.getEntityTypes().isEmpty() 
            && !Arrays.asList(rule.getEntityTypes()).contains(snapshot.entityType())) {
            return false;
        }
        
        // 2. Проверка entitySubtypes
        if (!rule.getEntitySubtypes().isEmpty()
            && (snapshot.entitySubtype() == null 
                || !Arrays.asList(rule.getEntitySubtypes()).contains(snapshot.entitySubtype()))) {
            return false;
        }
        
        // 3. Проверка attributeConditions
        if (rule.getAttributeConditions() != null && !rule.getAttributeConditions().isEmpty()) {
            return evaluateAttributeConditions(rule.getAttributeConditions(), snapshot.attributes());
        }
        
        return true; // все проверки пройдены
    }
    
    @SuppressWarnings("unchecked")
    private boolean evaluateAttributeConditions(
        Map<String, Object> attributeConditions,
        Map<String, Object> actualAttributes
    ) {
        for (Map.Entry<String, Object> entry : attributeConditions.entrySet()) {
            String attributeName = entry.getKey();
            Map<String, Object> predicates = (Map<String, Object>) entry.getValue();
            
            Object actualValue = actualAttributes.get(attributeName);
            
            if (!evaluatePredicates(actualValue, predicates)) {
                return false;
            }
        }
        return true;
    }
    
    @SuppressWarnings("unchecked")
    private boolean evaluatePredicates(Object actualValue, Map<String, Object> predicates) {
        for (Map.Entry<String, Object> predicate : predicates.entrySet()) {
            String operator = predicate.getKey();
            Object expectedValue = predicate.getValue();
            
            switch (operator) {
                case "eq" -> {
                    if (!Objects.equals(actualValue, expectedValue)) return false;
                }
                case "in" -> {
                    List<Object> allowedValues = (List<Object>) expectedValue;
                    if (!allowedValues.contains(actualValue)) return false;
                }
                case "gte" -> {
                    if (!(actualValue instanceof Number actual && expectedValue instanceof Number expected
                        && actual.doubleValue() >= expected.doubleValue())) {
                        return false;
                    }
                }
                case "lte" -> {
                    if (!(actualValue instanceof Number actual && expectedValue instanceof Number expected
                        && actual.doubleValue() <= expected.doubleValue())) {
                        return false;
                    }
                }
                case "gt" -> {
                    if (!(actualValue instanceof Number actual && expectedValue instanceof Number expected
                        && actual.doubleValue() > expected.doubleValue())) {
                        return false;
                    }
                }
                case "lt" -> {
                    if (!(actualValue instanceof Number actual && expectedValue instanceof Number expected
                        && actual.doubleValue() < expected.doubleValue())) {
                        return false;
                    }
                }
                case "contains" -> {
                    if (!(actualValue instanceof String actual && expectedValue instanceof String expected
                        && actual.contains(expected))) {
                        return false;
                    }
                }
                default -> throw new UnsupportedOperationException("Unknown operator: " + operator);
            }
        }
        return true;
    }
    
    private record TemplateWithPriority(Template template, int priority) {}
}
```

### 3. RouteGeneratorService

**Пакет:** `ru.coordination.approval.service`

```java
@Service
@Transactional
@RequiredArgsConstructor
public class RouteGeneratorService {
    private final TemplateRepository templateRepository;
    private final StageTemplateRepository stageTemplateRepository;
    private final SlotTemplateRepository slotTemplateRepository;
    private final ProcessRepository processRepository;
    private final StateMachineConfigRepository configRepository;
    private final RoleResolverAdapter roleResolverAdapter;
    private final RouteValidatorService validatorService;
    
    /**
     * Сгенерировать ProcessInstance из шаблона.
     * Процесс создаётся в статусе Draft, готов к запуску через ProcessService.startProcess().
     * 
     * @param templateId конкретная версия Template
     * @param entitySnapshot снимок сущности
     * @param initiatorId инициатор процесса
     * @return ProcessInstance со всей структурой (stages, iterations, participants)
     */
    public ProcessInstance generateRoute(
        UUID templateId,
        EntitySnapshot entitySnapshot,
        UUID initiatorId
    ) {
        // 1. Загрузить шаблон
        Template template = templateRepository.findById(templateId)
            .orElseThrow(() -> new TemplateNotFoundException(templateId));
        
        if (template.getStatus() != TemplateStatus.PUBLISHED) {
            throw new IllegalArgumentException("Template must be in PUBLISHED status");
        }
        
        // 2. Найти config version для процесса
        StateMachineConfig processConfig = configRepository
            .findByEntityTypeAndProcessTypeAndVersion(
                EntityType.PROCESS,
                template.getProcessType(),
                null // latest version
            )
            .orElseThrow(() -> new IllegalStateException("No config for PROCESS"));
        
        // 3. Создать ProcessInstance
        ProcessInstance process = createProcessInstance(template, entitySnapshot, initiatorId, processConfig);
        
        // 4. Создать StageInstance[] из StageTemplate[]
        List<StageTemplate> stageTemplates = stageTemplateRepository
            .findByTemplateIdOrderByOrderIdx(templateId);
        
        Instant baseDate = entitySnapshot.targetDate() != null 
            ? entitySnapshot.targetDate() 
            : Instant.now();
        
        for (StageTemplate stageTemplate : stageTemplates) {
            StageInstance stage = createStageInstance(stageTemplate, process, baseDate);
            process.getStages().add(stage);
            
            // 5. Создать StageIteration для этапа
            StageIteration iteration = createStageIteration(stage);
            stage.getIterations().add(iteration);
            
            // 6. Создать Participant[] из SlotTemplate[]
            List<SlotTemplate> slots = slotTemplateRepository.findByStageTemplateId(stageTemplate.getId());
            for (SlotTemplate slot : slots) {
                Participant participant = createParticipant(slot, iteration, entitySnapshot);
                iteration.getParticipants().add(participant);
            }
        }
        
        // 7. Валидация
        validatorService.validate(process);
        
        // 8. Сохранить (cascade persist)
        return processRepository.save(process);
    }
    
    private ProcessInstance createProcessInstance(
        Template template,
        EntitySnapshot snapshot,
        UUID initiatorId,
        StateMachineConfig processConfig
    ) {
        ProcessInstance.ProcessInstanceBuilder builder = ProcessInstance.builder()
            .id(UUID.randomUUID())
            .entityType(snapshot.entityType())
            .entitySubtype(snapshot.entitySubtype())
            .entityId(snapshot.entityId())
            .templateRef(template.getId()) // Snapshot-on-Start
            .processType(template.getProcessType())
            .configVersion(processConfig.getVersion())
            .status("Draft")
            .initiatorId(initiatorId)
            .createdAt(Instant.now())
            .version(0);
        
        // Для UNIFIED: резолвить ответственного
        if (template.getProcessType() == ProcessType.UNIFIED) {
            UUID responsibleUserId = resolveResponsible(template.getResponsibleRoles(), snapshot);
            builder.responsibleUserId(responsibleUserId);
            // responsibleResolvedRoleRef можно заполнить если RoleResolverAdapter вернёт роль
        }
        
        return builder.build();
    }
    
    private StageInstance createStageInstance(
        StageTemplate stageTemplate,
        ProcessInstance process,
        Instant baseDate
    ) {
        return StageInstance.builder()
            .id(UUID.randomUUID())
            .processId(process.getId())
            .orderIdx(stageTemplate.getOrderIdx())
            .originalOrderIdx(stageTemplate.getOrderIdx())
            .name(stageTemplate.getName())
            .description(stageTemplate.getDescription())
            .stageType(stageTemplate.getStageType())
            .duration(stageTemplate.getDuration())
            .decisionMode(stageTemplate.getDecisionMode())
            .executionOrder(stageTemplate.getExecutionOrder())
            .isMandatory(stageTemplate.getIsMandatory())
            .isOrderMandatory(stageTemplate.getIsOrderMandatory())
            .allowedReturnStages(stageTemplate.getAllowedReturnStages())
            .status("Pending")
            .dueDate(calculateDueDate(baseDate, stageTemplate.getDuration()))
            .build();
    }
    
    private StageIteration createStageIteration(StageInstance stage) {
        return StageIteration.builder()
            .id(UUID.randomUUID())
            .stageInstanceId(stage.getId())
            .iterationIdx(1) // первая итерация
            .status("Pending")
            .dueDate(stage.getDueDate())
            .build();
    }
    
    private Participant createParticipant(
        SlotTemplate slot,
        StageIteration iteration,
        EntitySnapshot entitySnapshot
    ) {
        UUID userId = resolveSlot(slot, entitySnapshot);
        
        if (slot.getRequired() && userId == null) {
            throw new RequiredSlotNotResolvedException(
                "Cannot resolve required slot at orderIdx=" + slot.getOrderIdx());
        }
        
        return Participant.builder()
            .id(UUID.randomUUID())
            .stageIterationId(iteration.getId())
            .orderIdx(slot.getOrderIdx())
            .userId(userId)
            .organizationId(slot.getOrganizationId())
            .status("Pending")
            .dueDate(iteration.getDueDate())
            .build();
    }
    
    private UUID resolveSlot(SlotTemplate slot, EntitySnapshot entitySnapshot) {
        // 1. Если userId явно задан → вернуть его
        if (slot.getUserId() != null) {
            return slot.getUserId();
        }
        
        // 2. Если acceptableRoles задан → резолвить через RoleResolverAdapter
        if (slot.getAcceptableRoles() != null && slot.getAcceptableRoles().length > 0) {
            for (UUID roleId : slot.getAcceptableRoles()) {
                List<UUID> candidates = roleResolverAdapter.resolveRole(
                    roleId,
                    slot.getOrganizationId(),
                    entitySnapshot
                );
                
                if (!candidates.isEmpty()) {
                    return candidates.get(0); // первый подходящий
                }
            }
        }
        
        // 3. Ничего не резолвилось
        return null;
    }
    
    private UUID resolveResponsible(UUID[] responsibleRoles, EntitySnapshot entitySnapshot) {
        if (responsibleRoles == null || responsibleRoles.length == 0) {
            throw new IllegalArgumentException("UNIFIED template must have responsibleRoles");
        }
        
        for (UUID roleId : responsibleRoles) {
            List<UUID> candidates = roleResolverAdapter.resolveRole(roleId, null, entitySnapshot);
            if (!candidates.isEmpty()) {
                return candidates.get(0);
            }
        }
        
        throw new ResponsibleNotResolvedException(
            "Cannot resolve responsible from roles: " + Arrays.toString(responsibleRoles));
    }
    
    private Instant calculateDueDate(Instant baseDate, int durationDays) {
        // Простой расчёт без учёта выходных (для PHASE-20 можно улучшить)
        return baseDate.plus(durationDays, ChronoUnit.DAYS);
    }
}
```

### 4. RouteValidatorService

**Пакет:** `ru.coordination.approval.service`

```java
@Service
@RequiredArgsConstructor
public class RouteValidatorService {
    /**
     * Валидировать сгенерированный маршрут.
     * Проверяет что все обязательные слоты заполнены, дедлайны корректны.
     */
    public void validate(ProcessInstance process) {
        // 1. Проверить что все required участники имеют userId
        for (StageInstance stage : process.getStages()) {
            for (StageIteration iteration : stage.getIterations()) {
                for (Participant participant : iteration.getParticipants()) {
                    // Проверяем через SlotTemplate был ли слот required
                    // Упрощённо: если userId=null и это не доп.согласующий → ошибка
                    if (participant.getUserId() == null) {
                        throw new RequiredSlotNotResolvedException(
                            "Participant at stage " + stage.getOrderIdx() 
                            + ", orderIdx " + participant.getOrderIdx() + " has no userId");
                    }
                }
            }
        }
        
        // 2. Проверить что дедлайны этапов идут по возрастанию
        Instant previousDueDate = null;
        for (StageInstance stage : process.getStages()) {
            if (previousDueDate != null && stage.getDueDate().isBefore(previousDueDate)) {
                throw new IllegalStateException(
                    "Stage " + stage.getOrderIdx() + " dueDate is before previous stage");
            }
            previousDueDate = stage.getDueDate();
        }
        
        // 3. Проверить что процесс имеет хотя бы один этап
        if (process.getStages().isEmpty()) {
            throw new IllegalStateException("Process must have at least one stage");
        }
    }
}
```

### 5. RoleResolverAdapter (stub для этой фазы)

**Пакет:** `ru.coordination.approval.adapter`

```java
public interface RoleResolverAdapter {
    /**
     * Резолвить роль в список пользователей.
     * 
     * @param roleId идентификатор роли
     * @param organizationId область поиска (опционально)
     * @param context снимок сущности для контекстного резолвинга
     * @return список userId, подходящих под роль (пустой если никто не найден)
     */
    List<UUID> resolveRole(UUID roleId, UUID organizationId, EntitySnapshot context);
}

@Component
public class RoleResolverAdapterStub implements RoleResolverAdapter {
    @Override
    public List<UUID> resolveRole(UUID roleId, UUID organizationId, EntitySnapshot context) {
        // Stub: всегда возвращает пустой список
        // Реальная реализация будет в PHASE-20
        return List.of();
    }
}
```

### 6. EntityAdapter (stub для тестов)

**Пакет:** `ru.coordination.approval.adapter`

```java
public interface EntityAdapter {
    /**
     * Получить снимок сущности из внешней системы.
     */
    EntitySnapshot fetchEntitySnapshot(String entityType, UUID entityId);
}

@Component
public class EntityAdapterStub implements EntityAdapter {
    @Override
    public EntitySnapshot fetchEntitySnapshot(String entityType, UUID entityId) {
        // Stub для тестов
        return new EntitySnapshot(
            entityType,
            null,
            entityId,
            Map.of("amount", 1000000, "priority", "HIGH"),
            Instant.now()
        );
    }
}
```

### 7. Exceptions

```java
public class NoMatchingTemplateException extends RuntimeException {
    public NoMatchingTemplateException(String message) {
        super(message);
    }
}

public class RequiredSlotNotResolvedException extends RuntimeException {
    public RequiredSlotNotResolvedException(String message) {
        super(message);
    }
}

public class ResponsibleNotResolvedException extends RuntimeException {
    public ResponsibleNotResolvedException(String message) {
        super(message);
    }
}

public class TemplateNotFoundException extends RuntimeException {
    public TemplateNotFoundException(UUID templateId) {
        super("Template not found: " + templateId);
    }
}
```

## Критерии приёмки

1. **Matching работает:**
   - Создан шаблон с ApplicabilityRule: `entityType="CONTRACT", amount.gte=1000000`
   - EntitySnapshot с `entityType="CONTRACT", amount=1500000` → шаблон матчится
   - EntitySnapshot с `entityType="CONTRACT", amount=500000` → не матчится

2. **Генерация ProcessInstance:**
   - `RouteGeneratorService.generateRoute()` создаёт ProcessInstance со статусом Draft
   - ProcessInstance.templateRef указывает на конкретный Template.id (Snapshot-on-Start)
   - Созданы StageInstance[] соответствующие StageTemplate[]
   - Созданы StageIteration[] для каждого этапа (iterationIdx=1)

3. **Генерация Participant:**
   - SlotTemplate с userId=<UUID> → Participant.userId=<UUID>
   - SlotTemplate с acceptableRoles=[roleId] → вызов RoleResolverAdapter.resolveRole()
   - Required слот без userId → RequiredSlotNotResolvedException

4. **Расчёт дедлайнов:**
   - baseDate + duration → корректный Instant
   - Дедлайны этапов идут по возрастанию (валидация не падает)

5. **Валидация:**
   - Процесс без этапов → IllegalStateException
   - Participant.userId=null для required слота → RequiredSlotNotResolvedException
   - Дедлайн следующего этапа раньше предыдущего → IllegalStateException

6. **UNIFIED:**
   - Шаблон UNIFIED с responsibleRoles → ProcessInstance.responsibleUserId заполнен
   - Не удалось резолвить ответственного → ResponsibleNotResolvedException

7. **Операторы attributeConditions:**
   - `eq`, `in`, `gte`, `lte`, `gt`, `lt`, `contains` работают корректно
   - Неизвестный оператор → UnsupportedOperationException

## Тестирование

### Юнит-тесты

- **MatchServiceTest:**
  - `ruleMatches()` для entityType, entitySubtype
  - `evaluatePredicates()` для каждого оператора (eq, in, gte, lte, gt, lt, contains)
  - Несколько шаблонов матчятся → выбирается с меньшим ruleIdx
  - Ни один не матчится → NoMatchingTemplateException

- **RouteGeneratorServiceTest:**
  - Mock всех репозиториев и RoleResolverAdapter
  - Генерация ProcessInstance с 2 этапами, по 2 участника на каждом
  - Required слот не резолвится → RequiredSlotNotResolvedException
  - UNIFIED шаблон → responsibleUserId заполнен

- **RouteValidatorServiceTest:**
  - Процесс с userId=null для participant → RequiredSlotNotResolvedException
  - Дедлайн stage2 < stage1 → IllegalStateException
  - Процесс без этапов → IllegalStateException

### Интеграционный тест

**RouteGenerationIntegrationTest** (Testcontainers, реальные миграции V1-V31):

```java
@Test
void generatesProcessFromTemplateWithMatching() {
    // 1. Создать и опубликовать шаблон через TemplateService
    Template template = templateService.create(TemplateCreateRequest.builder()
        .name("High-value contract approval")
        .processType(ProcessType.STANDARD)
        .stages(List.of(
            StageTemplateDto.builder()
                .orderIdx(1)
                .name("Legal review")
                .duration(5)
                .decisionMode(DecisionMode.AND)
                .actorSlots(List.of(
                    SlotTemplateDto.builder()
                        .orderIdx(1)
                        .userId(legalUserId) // конкретный пользователь
                        .required(true)
                        .build()
                ))
                .build()
        ))
        .applicabilityRules(List.of(
            ApplicabilityRuleDto.builder()
                .ruleIdx(1)
                .entityTypes(new String[]{"CONTRACT"})
                .attributeConditions(Map.of(
                    "amount", Map.of("gte", 1000000)
                ))
                .build()
        ))
        .build());
    
    templateService.publish(template.getId(), adminId);
    
    // 2. Matching
    EntitySnapshot snapshot = new EntitySnapshot(
        "CONTRACT",
        null,
        UUID.randomUUID(),
        Map.of("amount", 1500000),
        Instant.now()
    );
    
    List<Template> matches = matchService.findMatchingTemplates(snapshot);
    assertThat(matches).hasSize(1);
    assertThat(matches.get(0).getId()).isEqualTo(template.getId());
    
    // 3. Генерация
    ProcessInstance process = routeGeneratorService.generateRoute(
        template.getId(),
        snapshot,
        initiatorId
    );
    
    assertThat(process.getStatus()).isEqualTo("Draft");
    assertThat(process.getTemplateRef()).isEqualTo(template.getId());
    assertThat(process.getStages()).hasSize(1);
    
    StageInstance stage = process.getStages().get(0);
    assertThat(stage.getName()).isEqualTo("Legal review");
    assertThat(stage.getIterations()).hasSize(1);
    
    StageIteration iteration = stage.getIterations().get(0);
    assertThat(iteration.getParticipants()).hasSize(1);
    
    Participant participant = iteration.getParticipants().get(0);
    assertThat(participant.getUserId()).isEqualTo(legalUserId);
}

@Test
void throwsExceptionWhenRequiredSlotCannotBeResolved() {
    // Шаблон с required слотом, но acceptableRoles пусты и userId=null
    Template template = createTemplateWithRequiredSlotWithoutUserId();
    templateService.publish(template.getId(), adminId);
    
    EntitySnapshot snapshot = new EntitySnapshot("CONTRACT", null, UUID.randomUUID(), Map.of(), Instant.now());
    
    assertThrows(RequiredSlotNotResolvedException.class, () ->
        routeGeneratorService.generateRoute(template.getId(), snapshot, initiatorId)
    );
}

@Test
void snapshotOnStartFixatesTemplateVersion() {
    // Создать шаблон v1 и сгенерировать процесс
    Template v1 = createAndPublishTemplate();
    ProcessInstance process = routeGeneratorService.generateRoute(v1.getId(), snapshot, initiatorId);
    assertThat(process.getTemplateRef()).isEqualTo(v1.getId());
    
    // Обновить шаблон → v2
    Template v2 = templateService.update(v1.getId(), updateRequest);
    
    // Процесс всё ещё ссылается на v1
    ProcessInstance reloaded = processRepository.findById(process.getId()).orElseThrow();
    assertThat(reloaded.getTemplateRef()).isEqualTo(v1.getId()); // не изменился
}
```

## Открытые вопросы

1. **Что делать если RoleResolverAdapter вернул несколько userId для одного слота?**
   - **Решение:** Берём первого `candidates.get(0)`. В будущем (PHASE-20) можно добавить логику выбора (по нагрузке, по иерархии).

2. **Валидация attributeConditions при создании ApplicabilityRule (PHASE-14) или при matching (эта фаза)?**
   - **Решение:** При matching. В PHASE-14 сохраняется любой валидный JSON, в этой фазе проверяем операторы.

3. **Поведение при отсутствии ApplicabilityRule у шаблона:**
   - Пустой массив `applicabilityRules` → шаблон не матчится никогда? Или матчится всегда?
   - **Решение:** Если `applicabilityRules` пуст → шаблон матчится **всегда** (универсальный шаблон).

4. **Учёт выходных дней при расчёте дедлайнов:**
   - В этой фазе простой расчёт `baseDate + duration days`.
   - В PHASE-20 можно добавить `WorkingDaysCalculator` через адаптер.

5. **Можно ли создать процесс из DEPRECATED шаблона?**
   - **Решение:** Нет, только из PUBLISHED. Если шаблон DEPRECATED, нужно использовать новую версию.

## Ревью Cowork

*(Заполняется после реализации и проверки)*
