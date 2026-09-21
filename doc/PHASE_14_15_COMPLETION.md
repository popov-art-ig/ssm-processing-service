# ✅ Задача выполнена: Детальные тикеты PHASE-14 и PHASE-15

**Дата:** 2026-09-21  
**Исполнитель:** Claude Code  
**Статус:** Готово к реализации

---

## 🎯 Что было сделано

Созданы **два детальных тикета** для критического пути до MVP — фазы, которые позволят создавать процессы через REST API из шаблонов, а не программно.

### 📄 Созданные документы

| Файл | Размер | Описание |
|------|--------|----------|
| [PHASE-14-template-management.md](tickets/PHASE-14-template-management.md) | 25 KB | Управление шаблонами: CRUD, версионирование, публикация |
| [PHASE-15-route-generation.md](tickets/PHASE-15-route-generation.md) | 32 KB | Подбор шаблона и генерация ProcessInstance |
| [MVP_PHASES_14_15_SUMMARY.md](MVP_PHASES_14_15_SUMMARY.md) | 16 KB | Сводка по обеим фазам с оценками и планом |
| [INDEX.md](tickets/INDEX.md) | обновлён | Добавлены PHASE-14/15 в навигацию |

**Итого:** 3 новых файла + 1 обновлён, ~73 KB документации

---

## 📋 PHASE-14: Шаблоны маршрутов

**Тикет:** [tickets/PHASE-14-template-management.md](tickets/PHASE-14-template-management.md)  
**Story Points:** 13  
**Приоритет:** 🔴 Критический путь до MVP  
**Зависимости:** PHASE-04 ✅ (уже в main)

### Что входит

#### 1. TemplateService
- `create(request)` — создание шаблона в статусе Draft
- `update(templateId, request)` — редактирование или создание новой версии
- `publish(templateId)` — публикация (Draft → Published)
- `deprecate(templateId)` — снятие с публикации (Published → Deprecated)
- `archive(templateId)` — архивация (Deprecated → Archived)
- `getById()`, `findByNameAndStatus()`

#### 2. TemplateStateMachine
- **Переходы:** PublishTemplate, DeprecateTemplate, ArchiveTemplate
- **Guards:** 
  - AllMandatorySlotsValid — проверка required слотов
  - NoActiveProcesses — блокировка архивации при активных процессах
- **Actions:**
  - ValidateTemplateStructure — валидация STANDARD/UNIFIED, check-констрейнтов
  - SetPublishedTimestamp — фиксация времени публикации
  - SetArchivedTimestamp — фиксация времени архивации

#### 3. Версионирование (ADR-023)
```
Template v1 (Published)
    ↓ update()
Template v2 (Draft, parentTemplateId=v1.id, version=2)
Template v1 → Deprecated

ProcessInstance создан на v1 → templateRef=v1.id (не меняется)
```

**Fork & Drain:** старая версия остаётся Deprecated, активные процессы доживают на ней.

#### 4. Валидация при публикации
- Все `required=true` слоты имеют `userId` или `acceptableRoles`
- Все этапы имеют `duration > 0`
- Для STANDARD: `executionOrder` и `decisionMode` заполнены
- Для UNIFIED: `executionOrder` и `decisionMode` null
- Check-констрейнты SlotTemplate выполнены

#### 5. Миграция V30
- Конфигурация TemplateStateMachine (entity_type=TEMPLATE)
- Guards/Actions в реестры
- Статусы: Draft, Published, Deprecated, Archived

### Критерии готовности

✅ Можно создать шаблон с этапами и слотами  
✅ Публикация проверяет валидность структуры  
✅ Версионирование: update() Published создаёт v2 Draft  
✅ Fork & Drain работает  
✅ Архивация блокируется при активных процессах  

### Тестирование

**Юнит-тесты:**
- AllMandatorySlotsValidGuardTest (4 кейса)
- NoActiveProcessesGuardTest (3 кейса)
- ValidateTemplateStructureActionTest (6 кейсов)

**Интеграционные тесты (Testcontainers):**
1. Создание и публикация шаблона
2. Валидация блокирует публикацию (invalid slots)
3. Версионирование работает
4. Fork & Drain: процессы на старой версии доживают
5. Архивация блокируется активными процессами

---

## 📋 PHASE-15: Подбор и генерация маршрута

**Тикет:** [tickets/PHASE-15-route-generation.md](tickets/PHASE-15-route-generation.md)  
**Story Points:** 21 (самая большая фаза в проекте)  
**Приоритет:** 🔴 Критический путь до MVP  
**Зависимости:** PHASE-14

### Что входит

#### 1. MatchService — подбор шаблона по правилам

```java
List<Template> findMatchingTemplates(EntitySnapshot)
Template selectBestTemplate(List<Template>)
```

**Matching logic:**
- Проверка `entityType`, `entitySubtype`
- Проверка `attributeConditions` с операторами:
  - `eq` — equals
  - `in` — in array
  - `gte`, `lte`, `gt`, `lt` — числовые сравнения
  - `contains` — substring для строк
- Выбор по приоритету (`ruleIdx` — меньший = выше приоритет)

**Пример:**
```json
{
  "entityTypes": ["CONTRACT"],
  "attributeConditions": {
    "amount": { "gte": 1000000 },
    "priority": { "in": ["HIGH", "CRITICAL"] }
  }
}
```

#### 2. RouteGeneratorService — генерация ProcessInstance

```java
ProcessInstance generateRoute(UUID templateId, EntitySnapshot snapshot, UUID initiatorId)
```

**Алгоритм:**
1. Загрузить Template (должен быть PUBLISHED)
2. Создать ProcessInstance:
   - `templateRef = templateId` (Snapshot-on-Start)
   - `status = "Draft"`
   - Для UNIFIED: резолвить `responsibleUserId`
3. Создать StageInstance[] из StageTemplate[]
4. Создать StageIteration для каждого этапа (iterationIdx=1)
5. Создать Participant[] из SlotTemplate[] с резолвингом userId
6. Рассчитать дедлайны (baseDate + duration days)
7. Валидация через RouteValidatorService
8. Сохранить ProcessInstance (cascade persist)

#### 3. Резолвинг слотов

```java
private UUID resolveSlot(SlotTemplate slot, EntitySnapshot snapshot) {
    // 1. Явный userId имеет приоритет
    if (slot.getUserId() != null) {
        return slot.getUserId();
    }
    
    // 2. Резолвинг через роли
    if (slot.getAcceptableRoles() != null) {
        for (UUID roleId : slot.getAcceptableRoles()) {
            List<UUID> candidates = roleResolverAdapter.resolveRole(
                roleId, slot.getOrganizationId(), snapshot
            );
            if (!candidates.isEmpty()) {
                return candidates.get(0);
            }
        }
    }
    
    // 3. Не резолвилось
    return null; // валидация отловит для required слотов
}
```

#### 4. RouteValidatorService — валидация результата

```java
void validate(ProcessInstance process)
```

**Проверки:**
- Все required участники имеют `userId`
- Дедлайны этапов идут по возрастанию
- Процесс имеет хотя бы один этап

#### 5. RoleResolverAdapter (stub)

```java
public interface RoleResolverAdapter {
    List<UUID> resolveRole(UUID roleId, UUID organizationId, EntitySnapshot context);
}

@Component
public class RoleResolverAdapterStub implements RoleResolverAdapter {
    @Override
    public List<UUID> resolveRole(...) {
        return List.of(); // stub: всегда пустой список
        // Реальная реализация → PHASE-20
    }
}
```

#### 6. EntitySnapshot (DTO)

```java
public record EntitySnapshot(
    String entityType,
    String entitySubtype,
    UUID entityId,
    Map<String, Object> attributes, // для matching
    Instant targetDate // базовая дата для дедлайнов
) {}
```

### Критерии готовности

✅ EntitySnapshot → matching → ProcessInstance создан  
✅ Операторы работают: eq, in, gte, lte, gt, lt, contains  
✅ ProcessInstance.templateRef фиксирует версию (Snapshot-on-Start)  
✅ StageInstance[], StageIteration[], Participant[] созданы  
✅ Required слот без userId → RequiredSlotNotResolvedException  
✅ Дедлайны рассчитаны корректно  
✅ UNIFIED: responsibleUserId заполнен  

### Тестирование

**Юнит-тесты:**
- MatchServiceTest: ruleMatches(), evaluatePredicates() для каждого оператора
- RouteGeneratorServiceTest: мок-генерация с проверкой структуры
- RouteValidatorServiceTest: валидация required слотов, дедлайнов

**Интеграционные тесты (Testcontainers):**
1. Полный цикл: шаблон → matching → генерация → ProcessInstance
2. Required слот не резолвится → exception
3. Snapshot-on-Start: templateRef не меняется при обновлении шаблона
4. UNIFIED: responsibleUserId заполнен
5. Несколько шаблонов матчатся → выбор по ruleIdx

---

## 🔗 Зависимости и критический путь

```
PHASE-04 (активация этапа) ✅ В main
    ↓
PHASE-14 (шаблоны) 📋 Тикет готов ⭐
    ↓
PHASE-15 (генерация) 📋 Тикет готов
    ↓
PHASE-21 (REST API) 📝 Нужен тикет
    ↓
🎉 MVP READY
```

**Статус зависимостей:**
- ✅ PHASE-04 завершена и в main — можно начинать PHASE-14
- ✅ Нет блокирующих зависимостей
- ✅ Параллельно можно завершить PHASE-11 (ветка готова)

---

## 📈 Оценки и сроки

### Story Points

| Фаза | SP | Описание |
|------|----|----------|
| PHASE-14 | 13 | TemplateService, TemplateStateMachine, версионирование |
| PHASE-15 | 21 | MatchService, RouteGeneratorService, резолвинг |
| **Итого** | **34** | Критический путь до MVP (+ PHASE-21: 8 SP) |

### Прогноз по времени

**Velocity:** 7.7 SP/неделю (23 SP за 3 недели, фазы 1-8)

| Фаза | SP | Оценка времени |
|------|----|----------------|
| PHASE-14 | 13 | 1.7 недели (8-9 дней) |
| PHASE-15 | 21 | 2.7 недели (13-14 дней) |
| **Итого** | **34** | **4.5 недели** |

**Прогноз завершения:** середина-конец октября 2026

После этого останется PHASE-21 (REST API, 8 SP, ~1 неделя) для **production-ready MVP**.

---

## 🎯 Что даёт завершение этих фаз

### До (текущее состояние)

Процессы создаются **программно в коде**:

```java
ProcessInstance process = ProcessInstance.builder()
    .entityType("CONTRACT")
    .entityId(contractId)
    .templateRef(templateId)
    .build();

StageInstance stage1 = StageInstance.builder()
    .processId(process.getId())
    .orderIdx(1)
    .name("Legal review")
    .duration(5)
    .build();

Participant participant = Participant.builder()
    .stageIterationId(iteration.getId())
    .userId(legalUserId)
    .build();

// ... много ручного создания объектов
```

### После (с PHASE-14 + PHASE-15)

Процессы создаются **из шаблонов через сервис**:

```java
// 1. Создать и опубликовать шаблон (один раз)
Template template = templateService.create(TemplateCreateRequest.builder()
    .name("Contract approval > 1M")
    .processType(ProcessType.STANDARD)
    .stages(...)
    .applicabilityRules([{
        entityTypes: ["CONTRACT"],
        attributeConditions: { amount: { gte: 1000000 } }
    }])
    .build());

templateService.publish(template.getId(), adminId);

// 2. Создать процесс из шаблона (автоматически)
EntitySnapshot snapshot = new EntitySnapshot(
    "CONTRACT",
    null,
    contractId,
    Map.of("amount", 1500000),
    Instant.now()
);

// Подбор + генерация в одном вызове
List<Template> matches = matchService.findMatchingTemplates(snapshot);
Template best = matchService.selectBestTemplate(matches);
ProcessInstance process = routeGeneratorService.generateRoute(
    best.getId(),
    snapshot,
    initiatorId
);

// Процесс готов со всей структурой (stages, iterations, participants)
```

### После PHASE-21 (REST API)

Процессы создаются **через HTTP API**:

```bash
POST /processes
{
  "entityType": "CONTRACT",
  "entityId": "...",
  "attributes": { "amount": 1500000 }
}

Response:
{
  "id": "...",
  "status": "Draft",
  "stages": [...],
  "templateRef": "..."
}
```

---

## 🚀 Следующие шаги

### Немедленно (сегодня/завтра)

1. ✅ **Завершить PHASE-11** — создать PR для ветки `feature/phase-11-remarks-lifecycle` (коммит уже готов)
2. 🚧 **Начать PHASE-14** — рекомендуемая следующая фаза
   - Создать ветку `feature/phase-14-template-management`
   - Реализовать TemplateService
   - Миграция V30
   - Guards/Actions
   - Тесты

### Через 1-2 недели (после PHASE-14)

1. **Реализовать PHASE-15** (самая большая фаза)
   - Создать ветку `feature/phase-15-route-generation`
   - Реализовать MatchService
   - Реализовать RouteGeneratorService
   - Stub для RoleResolverAdapter
   - Тесты

### Через 4-5 недель (после PHASE-15)

1. **Подготовить тикет PHASE-21** (REST API)
2. **Реализовать PHASE-21** (8 SP, ~1 неделя)
3. **MVP готов!** 🎉

### Параллельные работы (опционально)

Пока идёт PHASE-14/15, можно подготовить детальные тикеты:
- PHASE-09 (Отзыв процесса) — 3 SP
- PHASE-10 (Доп.согласующие) — 5 SP
- PHASE-16 (Автосогласование) — 8 SP
- PHASE-19 (Event Publisher) — 8 SP
- PHASE-20 (Адаптеры реальные) — 8 SP

---

## 📚 Навигация по документам

### Детальные тикеты (готовы к реализации)

1. [PHASE-14-template-management.md](tickets/PHASE-14-template-management.md) — Управление шаблонами (25 KB)
2. [PHASE-15-route-generation.md](tickets/PHASE-15-route-generation.md) — Генерация маршрутов (32 KB)

### Сводки и индексы

- [MVP_PHASES_14_15_SUMMARY.md](MVP_PHASES_14_15_SUMMARY.md) — Сводка по PHASE-14/15 (16 KB)
- [INDEX.md](tickets/INDEX.md) — Индекс всех тикетов (обновлён)
- [REMAINING_PHASES.md](REMAINING_PHASES.md) — Полный план 24 фаз (38 KB)
- [PHASES_SUMMARY.md](PHASES_SUMMARY.md) — Краткая сводка с деревом зависимостей (12 KB)

### Ранее созданные тикеты

- [PHASE-05-participant-decision.md](tickets/PHASE-05-participant-decision.md) ✅ В main
- [PHASE-06-stage-aggregation.md](tickets/PHASE-06-stage-aggregation.md) ✅ В main
- [PHASE-07-process-completion.md](tickets/PHASE-07-process-completion.md) ✅ В main
- [PHASE-08-process-resume.md](tickets/PHASE-08-process-resume.md) ✅ В main
- [PHASE-11-remarks-lifecycle.md](tickets/PHASE-11-remarks-lifecycle.md) 🚧 В работе

---

## ✅ Чек-лист готовности тикетов

### PHASE-14

- ✅ Полное описание контекста и зависимостей
- ✅ Объём фазы (Scope) детализирован
- ✅ Явно указано что НЕ входит (Out of scope)
- ✅ Технические требования с сигнатурами классов/методов
- ✅ Алгоритм версионирования (ADR-023) описан пошагово
- ✅ Миграция V30 с DDL
- ✅ Guards/Actions со спецификацией
- ✅ Критерии приёмки (8 пунктов)
- ✅ План тестирования (юнит + интеграционные)
- ✅ Открытые вопросы с решениями
- ✅ Самодостаточность: можно реализовать без внешних документов

### PHASE-15

- ✅ Полное описание контекста и зависимостей
- ✅ Объём фазы (Scope) детализирован
- ✅ Явно указано что НЕ входит (Out of scope)
- ✅ Технические требования с сигнатурами
- ✅ Алгоритм matching с примерами операторов
- ✅ Алгоритм генерации ProcessInstance пошагово
- ✅ Алгоритм резолвинга слотов
- ✅ EntitySnapshot DTO описан
- ✅ RoleResolverAdapter stub спецификация
- ✅ Критерии приёмки (7 пунктов)
- ✅ План тестирования (юнит + интеграционные)
- ✅ Открытые вопросы с решениями
- ✅ Самодостаточность: можно реализовать без внешних документов

---

## 💡 Рекомендации для реализации

### PHASE-14

**Порядок реализации:**
1. Миграция V30 → Guards/Actions в реестры
2. TemplateService.create() + базовые CRUD
3. TemplateService.publish() + валидация
4. Версионирование (update для Published)
5. Тесты

**Потенциальные сложности:**
- Версионирование: копирование всех связанных сущностей (StageTemplate, SlotTemplate, ApplicabilityRule)
- Fork & Drain: нужно тщательно протестировать что процессы не меняют templateRef

**Митигация:**
- Использовать `@Transactional` для атомарности
- Cascade persist для автоматического сохранения связей
- Интеграционные тесты с реальными процессами

### PHASE-15

**Порядок реализации:**
1. EntitySnapshot DTO + RoleResolverAdapter stub
2. MatchService (matching logic для каждого оператора)
3. RouteValidatorService
4. RouteGeneratorService (генерация ProcessInstance)
5. Интеграция: MatchService + RouteGeneratorService
6. Тесты

**Потенциальные сложности:**
- Matching logic: много операторов, edge cases (null values, type mismatches)
- Резолвинг слотов: stub возвращает пустой список, тесты должны работать с явными userId
- Performance: генерация для больших шаблонов (10+ этапов, 50+ слотов) — N+1 запросы

**Митигация:**
- Параметризованные тесты для каждого оператора
- В тестовых шаблонах использовать явные userId, не роли
- Fetch Join для загрузки связанных сущностей (избежать N+1)

---

## 🎉 Итоги

### Выполнено

✅ Изучена вся документация проекта (doc/arch/, doc/tickets/, doc/tasks/)  
✅ Проанализированы завершённые фазы 1-8 + 11 в работе  
✅ Определены следующие критические фазы: PHASE-14, PHASE-15  
✅ Созданы 2 детальных тикета (57 KB) по шаблону TEMPLATE.md  
✅ Создана сводка с оценками и планом (16 KB)  
✅ Обновлён INDEX.md с навигацией  

### Готово к использованию

📋 **2 детальных тикета** готовы к немедленной реализации  
📊 **Оценки времени** на основе velocity (7.7 SP/неделю)  
🎯 **Критерии приёмки** для каждой фазы  
🧪 **План тестирования** (юнит + интеграционные)  
🔗 **Дерево зависимостей** и критический путь  

### Следующий шаг

**Начать реализацию PHASE-14** (Шаблоны маршрутов) — рекомендуемая следующая фаза.

Тикет полностью самодостаточен, все алгоритмы описаны, DDL миграции готов, можно начинать кодить! 🚀

---

**Дата создания:** 2026-09-21  
**Автор:** Claude Code  
**Версия:** 1.0
