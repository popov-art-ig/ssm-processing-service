# ✅ Задача выполнена: Детальный тикет REST API (PHASE-21-25)

**Дата:** 2026-09-22  
**Исполнитель:** Claude Code  
**Статус:** Готово к реализации

---

## 🎯 Что было сделано

Создан **объединённый детальный тикет** для REST API — фазы 21-25 объединены в единую фазу для более эффективной реализации.

### 📄 Созданный документ

| Файл | Размер | Описание |
|------|--------|----------|
| [PHASE-21-25-REST-API.md](tickets/PHASE-21-25-REST-API.md) | 42 KB | Полный REST API для UI (объединённые фазы 21-25) |
| [INDEX.md](tickets/INDEX.md) | обновлён | Добавлена PHASE-21-25, создана PHASE-29 |

**Итого:** 1 новый тикет (42 KB) + 1 обновлён

---

## 📋 PHASE-21-25: REST API (объединённая фаза)

**Тикет:** [tickets/PHASE-21-25-REST-API.md](tickets/PHASE-21-25-REST-API.md)  
**Story Points:** 27 (объединённые фазы 21-25)  
**Приоритет:** 🔴 Критический путь до MVP с UI  
**Зависимости:** PHASE-15, PHASE-08, PHASE-11, PHASE-14

### Почему объединили 5 фаз в одну?

**Оригинальные фазы:**
- PHASE-21: REST API — Процессы (8 SP)
- PHASE-22: REST API — Решения (3 SP)
- PHASE-23: REST API — Замечания и комментарии (3 SP)
- PHASE-24: REST API — Администрирование (5 SP)
- PHASE-25: REST API — Агрегированные эндпоинты (5 SP)

**Итого:** 24 SP → скорректировано до **27 SP** (добавлены mappers, exception handling, тесты)

**Причины объединения:**

1. **Общие компоненты:**
   - Exception handling (GlobalExceptionHandler)
   - Mappers (ProcessMapper, DecisionMapper, RemarkMapper, TemplateMapper)
   - DTOs используются между контроллерами
   - CORS и общая конфигурация

2. **Связность:**
   - Все контроллеры работают с одной domain-моделью
   - Тестировать лучше как единый API (Postman коллекция)
   - Агрегированные эндпоинты (PHASE-25) зависят от всех предыдущих

3. **Эффективность:**
   - Один PR вместо пяти
   - Единый набор интеграционных тестов
   - Единая Postman коллекция для демо

### Что входит

#### 1. ProcessController — управление процессами (группа 12 API)

**Ключевые эндпоинты:**

```
POST   /api/v1/processes                        # Создать процесс из entitySnapshot
GET    /api/v1/processes/{id}                   # Получить процесс
GET    /api/v1/processes                        # Список с фильтрами и пагинацией
POST   /api/v1/processes/{id}/start             # Запустить (Draft → InProgress)
POST   /api/v1/processes/{id}/recall            # Отозвать (InProgress → Recalled)
POST   /api/v1/processes/{id}/resume            # Возобновить (OnRework → InProgress)
```

**Фишки:**
- Автоматический подбор шаблона через MatchService
- Генерация ProcessInstance через RouteGeneratorService
- Фильтрация по status, initiatorId, entityType
- Пагинация через Spring Data Pageable

#### 2. DecisionController — принятие решений (группа 16 API)

**Ключевые эндпоинты:**

```
POST   /api/v1/processes/{processId}/stages/{stageId}/participants/{participantId}/decide
GET    /api/v1/processes/{processId}/stages/{stageId}/participants/{participantId}/decision
GET    /api/v1/processes/{processId}/decisions  # Все решения по процессу
```

**Фишки:**
- Поддержка решений: APPROVE, APPROVE_WITH_COMMENTS, REJECT
- Комментарии к решениям
- История всех решений по процессу

#### 3. RemarkController + CommentController — замечания (группа 17+18 API)

**Ключевые эндпоинты:**

```
POST   /api/v1/processes/{id}/remarks           # Создать замечание
PUT    /api/v1/processes/{id}/remarks/{remarkId}/resolve      # Исправить
PUT    /api/v1/processes/{id}/remarks/{remarkId}/accept       # Принять исправление
PUT    /api/v1/processes/{id}/remarks/{remarkId}/reject       # Отклонить замечание
PUT    /api/v1/processes/{id}/remarks/{remarkId}/reject-resolution  # Отклонить исправление
GET    /api/v1/processes/{id}/remarks           # Список с фильтром по status

POST   /api/v1/processes/{id}/comments          # Создать комментарий
GET    /api/v1/processes/{id}/comments          # Список с фильтрами
```

**Фишки:**
- Полный lifecycle замечаний через API: Proposed → Resolved → Accepted/Rejected
- Комментарии привязаны к процессу/этапу/замечанию
- Severity: CRITICAL, MAJOR, MINOR

#### 4. TemplateController — управление шаблонами (группа 10 API)

**Ключевые эндпоинты:**

```
POST   /api/v1/admin/templates                  # Создать шаблон
PUT    /api/v1/admin/templates/{id}             # Обновить (создаёт v2 если Published)
POST   /api/v1/admin/templates/{id}/publish     # Draft → Published
POST   /api/v1/admin/templates/{id}/deprecate   # Published → Deprecated
POST   /api/v1/admin/templates/{id}/archive     # Deprecated → Archived
GET    /api/v1/admin/templates/{id}
GET    /api/v1/admin/templates                  # Список с фильтрами
```

**Фишки:**
- Версионирование: update() Published создаёт новую версию (v2)
- Валидация при публикации
- Фильтрация по status, name

#### 5. StateMachineConfigController + GuardActionRegistryController (группа 23+24 API)

**Ключевые эндпоинты:**

```
GET    /api/v1/admin/state-machines             # Список конфигураций
GET    /api/v1/admin/state-machines/{id}        # Конфигурация со states/transitions
POST   /api/v1/admin/state-machines             # Создать новую версию конфигурации

GET    /api/v1/admin/registry/guards            # Список всех guards
GET    /api/v1/admin/registry/actions           # Список всех actions
```

**Фишки:**
- Интроспекция state machine (какие переходы доступны)
- Список guards/actions для конструирования конфигураций

#### 6. AdminController — администрирование (группа 22 API, **без PHASE-17/18**)

**Ключевые эндпоинты:**

```
POST   /api/v1/admin/processes/{id}/restore     # Восстановить из архива
```

**Не входит (перенесено в PHASE-29):**
- ❌ `GET/PUT /admin/notification-settings` (зависит от PHASE-17)
- ❌ Управление автоархивацией (зависит от PHASE-18)

#### 7. ApprovalViewController — агрегированные эндпоинты (группа 11 API)

**Ключевые эндпоинты:**

```
GET    /api/v1/approval-view/entities/{entityId}  # Полная информация за < 500ms
GET    /api/v1/approval-view/tasks/my             # Задачи пользователя
GET    /api/v1/approval-view/processes/my         # Процессы пользователя
```

**Фишки:**
- **Оптимизация:** один запрос вместо 10+ для UI
- Fetch Join для избежания N+1 запросов
- Цель: < 500ms для процесса с 10 этапами
- Агрегация: process + stages + participants + decisions + remarks + comments

**ApprovalViewDto структура:**

```java
record ApprovalViewDto(
    ProcessDto process,
    List<StageWithDecisionsDto> stages,  // этап + участники + решения
    List<RemarkDto> remarks,
    List<CommentDto> comments
)
```

#### 8. Общие компоненты

**Exception Handling:**

```java
@RestControllerAdvice
class GlobalExceptionHandler {
    // 404 для EntityNotFoundException
    // 400 для IllegalArgumentException
    // 409 для IllegalStateException
    // 500 для прочих
}
```

**Validation:**

```java
public record CreateProcessRequest(
    @NotBlank String entityType,
    @NotNull UUID entityId,
    @NotNull UUID initiatorId,
    @NotNull Map<String, Object> attributes
) {}
```

**Mappers:**

```java
@Component
class ProcessMapper {
    ProcessDto toDto(ProcessInstance process);
    StageDto toStageDto(StageInstance stage);
    // ... и т.д.
}
```

**CORS:**

```java
@Configuration
class WebConfig implements WebMvcConfigurer {
    // Разрешить localhost:3000, localhost:4200 для локальной разработки
}
```

### Производительность

**Требование:** `GET /approval-view/entities/{entityId}` должен выполняться **< 500ms** для процесса с 10 этапами.

**Оптимизация:**
1. Fetch Join для ProcessInstance + Stages + Iterations
2. Batch-загрузка Participants + Decisions (один запрос через `IN`)
3. Batch-загрузка Remarks + Comments
4. Избежание N+1 запросов

### Критерии готовности

✅ Все 70+ эндпоинтов реализованы  
✅ Exception handling работает (404, 400, 409, 500)  
✅ Validation работает для всех request DTOs  
✅ CORS настроен для локальной разработки  
✅ Пагинация работает через Pageable  
✅ Фильтрация работает для всех списковых эндпоинтов  
✅ ApprovalViewController < 500ms для процесса с 10 этапами  
✅ Postman коллекция с полными сценариями  
✅ Юнит-тесты (MockMvc) для всех контроллеров  
✅ Интеграционные тесты (Testcontainers) для критических сценариев  

### Что НЕ входит

❌ WebSocket для real-time обновлений  
❌ GraphQL API  
❌ Batch операции (массовое создание процессов)  
❌ Экспорт в PDF/Excel  
❌ Настройки уведомлений → PHASE-29  
❌ Управление автоархивацией → PHASE-29  
❌ Swagger/OpenAPI (опционально через springdoc-openapi)  

---

## 🆕 PHASE-29: REST API — Настройки уведомлений и автоархивации

**Статус:** Новая фаза (пока только концепция)  
**Story Points:** 3 (оценка)  
**Приоритет:** 🟢 Средний (после PHASE-17, PHASE-18)  
**Зависимости:** PHASE-17 (напоминания), PHASE-18 (автоархивация)

### Зачем создали PHASE-29?

В оригинальной PHASE-24 были эндпоинты для управления настройками уведомлений и автоархивации:
- `GET/PUT /admin/notification-settings`
- Эндпоинты для управления автоархивацией

Но эти эндпоинты **зависят от PHASE-17 и PHASE-18**, которые ещё не реализованы.

**Решение:** Вынести эти эндпоинты в отдельную фазу PHASE-29, которая будет реализована **после** PHASE-17 и PHASE-18.

**PHASE-21-25** теперь **не зависит от PHASE-17/18** и может быть реализована сразу после PHASE-14/15.

### Что войдёт в PHASE-29

```
GET    /api/v1/admin/notification-settings
PUT    /api/v1/admin/notification-settings
POST   /api/v1/admin/auto-archive/configure
GET    /api/v1/admin/auto-archive/config
```

Детальный тикет будет подготовлен позже.

---

## 🔗 Зависимости и критический путь

### До MVP с UI

```
PHASE-08 (возврат) ✅
PHASE-11 (замечания) 🚧
PHASE-14 (шаблоны) 📋
PHASE-15 (генерация) 📋
    ↓
PHASE-21-25 (REST API) 📋
    ↓
🎉 MVP с UI READY
```

**Критический путь:**
1. Завершить PHASE-11 (PR готов)
2. Реализовать PHASE-14 (13 SP, ~2 недели)
3. Реализовать PHASE-15 (21 SP, ~3 недели)
4. Реализовать PHASE-21-25 (27 SP, ~3.5 недели)

**Итого:** ~8.5 недель до MVP с UI (середина ноября 2026)

### Параллельные работы

Пока идёт PHASE-14/15, можно:
- Подготовить детальные тикеты для PHASE-09, PHASE-10, PHASE-16
- Начать разработку UI mock-up с заглушками

---

## 📈 Оценка времени

**Velocity:** ~7.7 SP/неделю

| Фаза | SP | Оценка времени |
|------|----|----------------|
| PHASE-21-25 | 27 | ~3.5 недели (17-18 дней) |

**Состав 27 SP:**
- Оригинальные фазы 21-25: 24 SP
- Mappers и DTOs: +1 SP
- Exception handling: +1 SP
- Интеграционные тесты: +1 SP

**Прогноз:** конец ноября 2026 (после PHASE-14/15)

---

## 🧪 Тестирование

### Юнит-тесты (MockMvc)

**Покрытие:**
- ProcessControllerTest (6 тестов)
- DecisionControllerTest (3 теста)
- RemarkControllerTest (5 тестов)
- TemplateControllerTest (5 тестов)
- ApprovalViewControllerTest (3 теста)

**Итого:** ~25 юнит-тестов

### Интеграционные тесты (Testcontainers)

**Сценарии:**
1. Полный цикл: создание процесса → запуск → решения → завершение
2. Возврат на доработку: процесс → reject → resume
3. Замечания: создание → исправление → принятие
4. Шаблоны: создание → публикация → версионирование
5. ApprovalView: производительность < 500ms

**Итого:** ~5 интеграционных тестов

### Postman коллекция

**Структура:**

```
approval-api.postman_collection.json
├── 1. Setup (создать и опубликовать шаблон)
├── 2. Happy Path (процесс от создания до Approved)
├── 3. Rework Path (процесс с возвратом на доработку)
├── 4. Remarks (полный lifecycle замечаний)
└── 5. Aggregated Views (оптимизированные эндпоинты)
```

**Environment variables:**
```json
{
  "baseUrl": "http://localhost:8080/api/v1",
  "adminId": "{{$guid}}",
  "initiatorId": "{{$guid}}",
  "processId": "",
  "templateId": ""
}
```

---

## 📦 Deliverables

После реализации PHASE-21-25:

**Код:**
- 7 контроллеров (Process, Decision, Remark, Comment, Template, StateMachineConfig, ApprovalView)
- GlobalExceptionHandler
- 4 мапера (Process, Decision, Remark, Template)
- 20+ DTOs
- WebConfig (CORS)

**Тесты:**
- ~25 юнит-тестов (MockMvc)
- ~5 интеграционных тестов (Testcontainers)

**Документация:**
- Postman коллекция с примерами
- README обновлён с API примерами

---

## 🎯 Критерии успеха MVP с UI

После PHASE-21-25:

✅ **UI может начать разработку** — все API готовы  
✅ **Полный цикл через API** — создание, запуск, решения, завершение  
✅ **Оптимизированные эндпоинты** — < 500ms для approval view  
✅ **Postman для демо** — готовая коллекция для стейкхолдеров  

**Демо для стейкхолдеров:**

```bash
# 1. Создать и опубликовать шаблон
POST /admin/templates + POST /admin/templates/{id}/publish

# 2. Создать процесс (шаблон подбирается автоматически)
POST /processes { entityType, entityId, attributes }
→ ProcessInstance создан в статусе Draft

# 3. Запустить
POST /processes/{id}/start
→ InProgress, первый этап активен

# 4. Участники принимают решения
POST .../participants/{id}/decide { decision: "APPROVE" }
→ Этап закрывается, активируется следующий

# 5. Процесс завершается
→ Status: Approved, completedAt заполнен

# 6. UI отображает полную информацию
GET /approval-view/entities/{entityId}
→ Весь процесс с решениями и замечаниями за < 500ms
```

---

## 🚀 Следующие шаги

### Немедленно (текущая неделя)

1. ✅ **Завершить PHASE-11** — создать PR (ветка готова)
2. 🚧 **Начать PHASE-14** — шаблоны (детальный тикет готов)

### Через 2 недели (после PHASE-14)

1. **Реализовать PHASE-15** (генерация из шаблонов)
2. Параллельно можно начать подготовку UI mockup

### Через 5 недель (после PHASE-15)

1. **Реализовать PHASE-21-25** (REST API)
2. Подготовить Postman коллекцию для демо
3. **MVP с UI готов!** 🎉

### После MVP

1. PHASE-19 (Event Publisher для уведомлений)
2. PHASE-17 (Напоминания)
3. PHASE-18 (Автоархивация)
4. PHASE-29 (REST API для настроек уведомлений)
5. PHASE-20 (Реальные адаптеры)

---

## 💡 Заметки для реализации

### Порядок реализации (рекомендуемый)

1. **Общие компоненты:**
   - DTOs
   - Mappers
   - GlobalExceptionHandler
   - WebConfig (CORS)

2. **ProcessController** (самый важный):
   - POST /processes (создание)
   - POST /processes/{id}/start (запуск)
   - GET /processes/{id}, GET /processes (чтение)
   - POST /processes/{id}/recall, /resume (управление)

3. **DecisionController:**
   - POST .../decide
   - GET .../decision, GET /decisions

4. **RemarkController + CommentController:**
   - POST /remarks
   - PUT /remarks/{id}/resolve, /accept, /reject
   - POST /comments

5. **TemplateController:**
   - POST /templates
   - POST /templates/{id}/publish
   - GET /templates

6. **AdminController + StateMachineConfig + Registry:**
   - GET /state-machines
   - GET /registry/guards, /actions
   - POST /processes/{id}/restore

7. **ApprovalViewController** (последним):
   - GET /entities/{entityId} с оптимизацией
   - GET /tasks/my
   - GET /processes/my

### Риски и митигация

⚠️ **PHASE-21-25 — большая фаза (27 SP)**  
  → Митигация: разбить на 2-3 PR (контроллеры + агрегированные эндпоинты + тесты)

⚠️ **Производительность ApprovalViewController**  
  → Митигация: ранее профилирование, Fetch Join, batch loading

⚠️ **Синхронизация с UI-командой**  
  → Митигация: Postman коллекция как контракт, swagger (опционально)

⚠️ **Без аутентификации (actorId в request body)**  
  → Митигация: задокументировать, в будущем добавить Spring Security

---

## 📚 Итоги

### Выполнено

✅ Изучена спецификация REST API (doc/arch/07_api_contract.md)  
✅ Объединены фазы 21-25 в единую PHASE-21-25 (27 SP)  
✅ Создан детальный тикет (42 KB) с полной спецификацией  
✅ Создана PHASE-29 для эндпоинтов зависящих от PHASE-17/18  
✅ Обновлён INDEX.md с новой структурой  
✅ Убрана зависимость PHASE-21-25 от PHASE-17/18 → критический путь короче  

### Готово к использованию

📋 **Детальный тикет PHASE-21-25** готов к реализации  
📊 **27 SP** — ~3.5 недели работы  
🎯 **70+ эндпоинтов** полностью специфицированы  
🧪 **План тестирования** (юнит + интеграционные + Postman)  
🔗 **Критический путь** до MVP с UI обновлён  

### Прогресс проекта

**До MVP с UI осталось:**
- PHASE-14 (13 SP) — шаблоны
- PHASE-15 (21 SP) — генерация
- PHASE-21-25 (27 SP) — REST API

**Итого:** 61 SP, ~8 недель работы (середина ноября 2026)

---

**Дата создания:** 2026-09-22  
**Автор:** Claude Code  
**Версия:** 1.0
