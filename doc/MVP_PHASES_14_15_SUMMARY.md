# Сводка по PHASE-14 и PHASE-15 — Критический путь до MVP

**Дата:** 2026-09-21  
**Статус:** Детальные тикеты готовы к реализации

---

## 🎯 Цель

Закрыть критический путь до **production-ready MVP**: после этих двух фаз процессы можно будет создавать через REST API из шаблонов, а не программно в коде.

**Результат:** `POST /processes {entitySnapshot}` → ProcessInstance готов к запуску через `POST /processes/{id}/start`.

---

## 📊 Текущий статус проекта

### Выполнено (8 фаз, 65 SP)

| Фаза | Название | Статус |
|------|----------|--------|
| PHASE-01 | Базовая инфраструктура | ✅ В main |
| PHASE-02 | State Machine Engine | ✅ В main |
| PHASE-03 | ProcessService.startProcess | ✅ В main |
| PHASE-04 | Активация первого этапа | ✅ В main |
| PHASE-05 | Принятие решения участником | ✅ В main |
| PHASE-06 | Агрегация решений | ✅ В main |
| PHASE-07 | Завершение процесса | ✅ В main |
| PHASE-08 | Возврат на доработку | ✅ В main |

### В работе (1 фаза, 8 SP)

| Фаза | Название | Статус |
|------|----------|--------|
| PHASE-11 | Замечания и комментарии | 🚧 Ветка готова, нужен PR |

### Следующие для MVP (2 фазы, 34 SP)

| Фаза | Название | SP | Статус |
|------|----------|----|--------|
| **PHASE-14** | **Шаблоны маршрутов** | **13** | 📋 **Детальный тикет готов** ⭐ |
| **PHASE-15** | **Генерация из шаблонов** | **21** | 📋 **Детальный тикет готов** |

---

## 📋 PHASE-14: Шаблоны маршрутов (Template Management)

**Тикет:** [doc/tickets/PHASE-14-template-management.md](PHASE-14-template-management.md)  
**Story Points:** 13  
**Приоритет:** 🔴 Критический

### Что реализуется

1. **TemplateService** — управление шаблонами:
   - `create(request)` — создание шаблона в статусе Draft
   - `update(templateId, request)` — редактирование или создание новой версии
   - `publish(templateId)` — публикация (Draft → Published)
   - `deprecate(templateId)` — снятие с публикации (Published → Deprecated)
   - `archive(templateId)` — архивация (Deprecated → Archived)

2. **TemplateStateMachine** — жизненный цикл шаблонов:
   - Переходы: PublishTemplate, DeprecateTemplate, ArchiveTemplate
   - Guards: AllMandatorySlotsValid, NoActiveProcesses
   - Actions: ValidateTemplateStructure, SetPublishedTimestamp, SetArchivedTimestamp

3. **Версионирование (ADR-023)**:
   - При `update()` опубликованного шаблона создаётся **новая строка** Template:
     - Новый `id`, `version = старая + 1`, `parentTemplateId = старый.id`
     - Статус новой версии = Draft
   - Старая версия → Deprecated (Fork & Drain)
   - Активные процессы доживают на старой версии (ProcessInstance.templateRef не меняется)

4. **Валидация при публикации**:
   - Все обязательные слоты (`required=true`) имеют `userId` или `acceptableRoles`
   - Все этапы имеют `duration > 0`
   - Для STANDARD: `executionOrder` и `decisionMode` заполнены
   - Check-констрейнты SlotTemplate выполнены

5. **Миграция V30**:
   - Конфигурация TemplateStateMachine (entity_type=TEMPLATE)
   - Guards/Actions в реестры
   - Статусы (Draft, Published, Deprecated, Archived)

### Критерии готовности

✅ Можно создать шаблон с этапами и слотами  
✅ Публикация проверяет валидность структуры  
✅ Версионирование работает: update() опубликованного создаёт v2  
✅ Fork & Drain: старая версия Deprecated, процессы на ней доживают  
✅ Архивация блокируется если есть активные процессы (guard)

### Что НЕ входит

❌ Генерация ProcessInstance → PHASE-15  
❌ Подбор шаблона по entitySnapshot → PHASE-15  
❌ REST API для шаблонов → PHASE-24  
❌ Удаление шаблонов (только архивация)

---

## 📋 PHASE-15: Подбор и генерация маршрута (Route Matching & Generation)

**Тикет:** [doc/tickets/PHASE-15-route-generation.md](PHASE-15-route-generation.md)  
**Story Points:** 21 (самая большая фаза)  
**Приоритет:** 🔴 Критический

### Что реализуется

1. **MatchService** — подбор подходящего шаблона:
   - `findMatchingTemplates(EntitySnapshot)` — находит все Template, чьи ApplicabilityRule матчатся
   - `selectBestTemplate(List<Template>)` — выбирает по приоритету (ruleIdx)
   - Matching logic: проверка entityType, entitySubtype, attributeConditions
   - Поддержка операторов: `eq`, `in`, `gte`, `lte`, `gt`, `lt`, `contains`

2. **RouteGeneratorService** — генерация ProcessInstance из шаблона:
   - `generateRoute(templateId, entitySnapshot, initiatorId)` → ProcessInstance
   - Создаёт ProcessInstance (статус Draft, Snapshot-on-Start для templateRef)
   - Клонирует StageInstance[] из StageTemplate[]
   - Создаёт StageIteration для каждого этапа (iterationIdx=1)
   - Создаёт Participant[] из SlotTemplate[] с резолвингом ролей
   - Рассчитывает дедлайны (baseDate + duration рабочих дней)
   - **Не запускает процесс** — остаётся в Draft

3. **Резолвинг слотов**:
   - Если SlotTemplate.userId задан → используется напрямую
   - Если SlotTemplate.acceptableRoles задан → вызов RoleResolverAdapter
   - Required слот без userId → RequiredSlotNotResolvedException

4. **RoleResolverAdapter** (stub для этой фазы):
   - `resolveRole(roleId, organizationId, context)` → List<UUID> userIds
   - Stub возвращает пустой список (валидация отловит для required слотов)
   - Реальная реализация → PHASE-20

5. **RouteValidatorService**:
   - Проверяет что все обязательные слоты заполнены
   - Проверяет корректность дедлайнов (следующий этап > предыдущего)
   - Проверяет что участники имеют userId

6. **EntitySnapshot** (DTO):
   ```java
   record EntitySnapshot(
       String entityType,
       String entitySubtype,
       UUID entityId,
       Map<String, Object> attributes, // для matching
       Instant targetDate // базовая дата для дедлайнов
   )
   ```

### Алгоритм генерации

**Входные данные:**
- `templateId` — конкретная версия Template
- `EntitySnapshot` — снимок сущности (entityType, entityId, attributes)
- `initiatorId` — инициатор процесса

**Шаги:**

1. Загрузить Template (статус должен быть PUBLISHED)
2. Создать ProcessInstance:
   - `templateRef = templateId` (Snapshot-on-Start)
   - `status = "Draft"`
   - Для UNIFIED: резолвить `responsibleUserId` через `responsibleRoles`
3. Для каждого StageTemplate:
   - Создать StageInstance с расчётом `dueDate`
   - Создать StageIteration (iterationIdx=1)
   - Для каждого SlotTemplate создать Participant с резолвингом userId
4. Валидация через RouteValidatorService
5. Сохранить ProcessInstance (cascade persist)

### Критерии готовности

✅ EntitySnapshot → подбор шаблона → ProcessInstance создан  
✅ Matching работает для всех операторов (eq, in, gte, lte, gt, lt, contains)  
✅ ProcessInstance.templateRef фиксирует версию шаблона  
✅ StageInstance[], StageIteration[], Participant[] созданы корректно  
✅ Required слот без userId → exception  
✅ Дедлайны рассчитаны корректно  
✅ UNIFIED: responsibleUserId заполнен

### Что НЕ входит

❌ Запуск процесса (StartProcess) → уже реализован в PHASE-03  
❌ REST API → PHASE-21  
❌ Реальная реализация RoleResolverAdapter → PHASE-20  
❌ WorkingDaysCalculator (учёт выходных) → PHASE-20  
❌ Batch-генерация множества процессов

---

## 🔗 Зависимости

```
PHASE-04 (активация этапа) ✅
    ↓
PHASE-14 (шаблоны)
    ↓
PHASE-15 (генерация из шаблонов)
    ↓
PHASE-21 (REST API для создания процессов)
    ↓
🎉 MVP READY
```

**Критический путь:**
- PHASE-14 зависит только от PHASE-04 ✅ (уже в main)
- PHASE-15 зависит только от PHASE-14
- После PHASE-15 можно сразу переходить к PHASE-21 (REST API)

**Параллельные работы:** Пока идёт реализация PHASE-14/15, можно параллельно:
- Завершить PHASE-11 (PR для замечаний)
- Подготовить тикеты для PHASE-09, PHASE-10 (отзыв, доп.согласующие)

---

## 📈 Оценка времени

**Velocity:** ~7.7 SP/неделю (на основе последних 3 недель: 23 SP за 3 недели)

| Фаза | SP | Оценка времени |
|------|----|----------------|
| PHASE-14 | 13 | ~1.7 недели (8-9 рабочих дней) |
| PHASE-15 | 21 | ~2.7 недели (13-14 рабочих дней) |
| **Итого** | **34** | **~4.5 недели** |

**Прогноз завершения:** середина-конец октября 2026

После этого останется только PHASE-21 (REST API, 8 SP, ~1 неделя) для **production-ready MVP**.

---

## 🧪 Тестирование

### PHASE-14

**Юнит-тесты:**
- AllMandatorySlotsValidGuard (required слоты с/без userId)
- NoActiveProcessesGuard (блокировка архивации)
- ValidateTemplateStructureAction (проверка STANDARD/UNIFIED, check-констрейнты)

**Интеграционные тесты (Testcontainers):**
1. Создание и публикация шаблона
2. Валидация блокирует публикацию (invalid slots)
3. Версионирование: update() Published → создаёт v2
4. Fork & Drain: процессы на v1 доживают
5. Архивация блокируется активными процессами

### PHASE-15

**Юнит-тесты:**
- MatchService.ruleMatches() для каждого оператора
- MatchService.evaluatePredicates() (eq, in, gte, lte, gt, lt, contains)
- RouteValidatorService (required слоты, дедлайны, пустой процесс)

**Интеграционные тесты (Testcontainers):**
1. Полный цикл: шаблон → matching → генерация → ProcessInstance с участниками
2. Required слот не резолвится → exception
3. Snapshot-on-Start: templateRef фиксируется, не меняется при обновлении шаблона
4. UNIFIED: responsibleUserId заполнен корректно
5. Несколько шаблонов матчатся → выбирается по ruleIdx

---

## 📦 Deliverables

После реализации обеих фаз:

1. **Код:**
   - `TemplateService` с CRUD операциями
   - `MatchService` с matching logic
   - `RouteGeneratorService` с генерацией ProcessInstance
   - `RouteValidatorService`
   - `TemplateStateMachine` (конфигурация V30)
   - Guards: AllMandatorySlotsValid, NoActiveProcesses
   - Actions: ValidateTemplateStructure, SetPublishedTimestamp, SetArchivedTimestamp
   - RoleResolverAdapter (stub)

2. **Миграции:**
   - V30__seed_template_state_machine.sql

3. **Тесты:**
   - 15+ юнит-тестов
   - 10+ интеграционных тестов
   - Покрытие критических путей

4. **Документация:**
   - README обновлён с примерами использования TemplateService
   - JavaDoc для публичных API

---

## 🎯 Критерии успеха MVP

После PHASE-14 + PHASE-15 + PHASE-21:

✅ **Процессы создаются через API**, а не программно  
✅ **Шаблоны версионируются** — старые процессы доживают на старых версиях  
✅ **Matching автоматический** — система сама подбирает шаблон по entitySnapshot  
✅ **Резолвинг слотов** работает (пока через stub, реальный в PHASE-20)  
✅ **Валидация** не пропускает невалидные маршруты  

**Демо для стейкхолдеров:**
```bash
# 1. Создать шаблон
POST /admin/templates
{
  "name": "Contract approval > 1M",
  "processType": "STANDARD",
  "stages": [...],
  "applicabilityRules": [{
    "entityTypes": ["CONTRACT"],
    "attributeConditions": {"amount": {"gte": 1000000}}
  }]
}

# 2. Опубликовать
POST /admin/templates/{id}/publish

# 3. Создать процесс из шаблона
POST /processes
{
  "entityType": "CONTRACT",
  "entityId": "...",
  "attributes": {"amount": 1500000}
}
→ ProcessInstance создан, готов к запуску

# 4. Запустить
POST /processes/{id}/start
→ Процесс в InProgress, первый этап активен, участники назначены
```

---

## 🚀 Следующие шаги

### Сейчас (немедленно)

1. ✅ **Создать PR для PHASE-11** (ветка готова, нужен только PR)
2. 🚧 **Начать реализацию PHASE-14** — рекомендуемая следующая фаза

### После PHASE-14

1. **Реализовать PHASE-15** (самая большая фаза, 21 SP)
2. Параллельно можно готовить PHASE-21 (REST API)

### После PHASE-15

1. **PHASE-21** (REST API) — 8 SP, ~1 неделя
2. **MVP готов!** 🎉

### Параллельные работы (опционально)

Пока идёт PHASE-14/15, можно подготовить детальные тикеты для:
- PHASE-09 (Отзыв процесса) — 3 SP
- PHASE-10 (Доп.согласующие) — 5 SP
- PHASE-16 (Автосогласование) — 8 SP

---

## 💡 Заметки для реализации

### PHASE-14

- **Версионирование** — ключевая часть, нужно тщательно протестировать Fork & Drain
- **Валидация** при публикации критична — невалидные шаблоны не должны попадать в Published
- **Check-констрейнты** SlotTemplate уже в БД, нужно только проверить в Action

### PHASE-15

- **Matching logic** — самая сложная часть, много edge cases для операторов
- **Резолвинг слотов** — stub возвращает пустой список, тесты должны работать с явными userId в шаблонах
- **Snapshot-on-Start** — важно что ProcessInstance.templateRef **не меняется** при обновлении шаблона
- **Performance:** генерация может быть медленной для больших шаблонов (10+ этапов, 50+ слотов) — следить за N+1 запросами

### Риски

⚠️ **PHASE-15 — самая большая фаза (21 SP)** — может занять дольше оценки  
  → Митигация: разбить на 2 PR: сначала MatchService + тесты, потом RouteGeneratorService

⚠️ **RoleResolverAdapter stub** — тесты должны работать без реального резолвинга  
  → Митигация: в шаблонах для тестов использовать явные userId, не роли

⚠️ **Сложность matching logic** — много операторов, edge cases  
  → Митигация: параметризованные тесты для каждого оператора

---

**Документы:**
- [PHASE-14-template-management.md](PHASE-14-template-management.md) — 25 KB, детальный тикет
- [PHASE-15-route-generation.md](PHASE-15-route-generation.md) — 32 KB, детальный тикет
- [INDEX.md](INDEX.md) — обновлён, добавлены PHASE-14/15

**Готово к реализации!** ⭐
