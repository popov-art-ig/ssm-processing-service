# Контракт API

**Версия:** 2.1  
**Назначение:** полное описание REST API модуля «Согласование»

---

## Оглавление

1. Назначение и обоснование
2. Ключевые понятия
3. Принципы API
4. Соглашения и формат
5. Аутентификация и авторизация
6. Версионирование
7. Идемпотентность
8. Пагинация, фильтрация, сортировка
9. Обработка ошибок
10. Группа 1: Шаблоны маршрутов
11. Группа 2: Подбор шаблона
12. Группа 3: Процессы
13. Группа 4: Этапы и итерации
14. Группа 5: Участники
15. Группа 6: Дополнительные согласующие
16. Группа 7: Решения
17. Группа 8: Замечания
18. Группа 9: Комментарии
19. Группа 10: Возврат на этап
20. Группа 11: Ревизии документа
21. Группа 12: Финальное решение
22. Группа 13: Архивация
23. Группа 14: Конфигурация карт переходов
24. Группа 15: Реестр guards и actions
25. Группа 16: Агрегированные эндпоинты для Front
26. Группа 17: Настройки уведомлений
27. Сводная таблица эндпоинтов

---

## 1. Назначение и обоснование

### 1.1 Что такое контракт API

**Контракт API** — формальное описание всех REST-эндпоинтов модуля. Определяет:
- какие эндпоинты существуют;
- какие HTTP-методы используются;
- какие параметры принимаются;
- какие данные возвращаются;
- какие ошибки возможны.

### 1.2 Зачем нужен контракт

**Клиенты:**

| Клиент | Что делает |
|---|---|
| Интерфейс пользователя (UI) | Отображает процессы, отправляет действия |
| Система-владелец сущности | Создаёт процессы, получает статусы |
| Админ-панель | Управляет шаблонами и картами переходов |
| Внешние системы | Интегрируются через API |

Без чёткого контракта:
- Клиенты не знают, как вызывать сервис.
- Изменение API ломает клиентов.
- Нет автогенерации клиентов.
- Сложно тестировать.

### 1.3 Что даёт

| Что | Зачем |
|---|---|
| Единый формат | Все эндпоинты одинаковы |
| OpenAPI | Автогенерация клиентов, документации, тестов |
| Версионирование | Безопасное изменение |
| Идемпотентность | Безопасные повторные вызовы |
| Стандартные ошибки | Понятная обработка |
| Пагинация | Работа с большими списками |
| Агрегация | Готовые модели для Front |

---

## 2. Ключевые понятия

### 2.1 REST

**REST** — архитектурный стиль. Основные принципы:
- ресурсы идентифицируются URL;
- действия — через HTTP-методы;
- идемпотентность;
- кэширование.

### 2.2 Ресурс (Resource)

**Ресурс** — сущность, к которой обращаются через API.

**Примеры:**
- `/templates` — коллекция шаблонов
- `/processes/{id}` — конкретный процесс
- `/entities/approval-view` — агрегированное представление

### 2.3 HTTP-методы

| Метод | Описание | Идемпотентный |
|---|---|---|
| **GET** | Получить ресурс | Да |
| **POST** | Создать ресурс | Нет (с Idempotency-Key — да) |
| **PUT** | Заменить ресурс | Да |
| **PATCH** | Частично изменить | Нет |
| **DELETE** | Удалить ресурс | Да |

### 2.4 Коды ответов

| Код | Описание |
|---|---|
| 200 OK | Успешный запрос |
| 201 Created | Ресурс создан |
| 204 No Content | Успех без тела |
| 304 Not Modified | Ресурс не изменился |
| 400 Bad Request | Ошибка валидации |
| 401 Unauthorized | Не аутентифицирован |
| 403 Forbidden | Нет прав |
| 404 Not Found | Не найден |
| 409 Conflict | Конфликт |
| 422 Unprocessable Entity | Бизнес-ошибка |
| 500 Internal Server Error | Ошибка сервера |

### 2.5 DTO

**DTO** (Data Transfer Object) — структура в запросе или ответе.

### 2.6 Идемпотентность

**Идемпотентность** — свойство операции, при котором повторный вызов даёт тот же результат.

**Как.** Заголовок `Idempotency-Key`.

### 2.7 ETag

**ETag** — версия ресурса для оптимистичной блокировки.

### 2.8 Агрегированный эндпоинт

**Агрегированный эндпоинт** — возвращает **готовую модель** для экрана Front. Собирает данные за один запрос.

---

## 3. Принципы API

### 3.1 Ресурсный подход

API строится вокруг **ресурсов**, а не действий.

**Правильно:** `POST /processes/{id}/start`  
**Неправильно:** `POST /startProcess`

### 3.2 Единообразие

Все эндпоинты имеют одинаковую структуру:
- базовый URL с версией;
- ресурсный путь;
- стандартные заголовки;
- стандартные ошибки.

### 3.3 Безопасность

- HTTPS.
- JWT-токен.
- Авторизация на уровне ресурса и роли.
- Валидация входных данных.

### 3.4 Идемпотентность

- GET, PUT, DELETE — идемпотентны.
- POST — идемпотентен с `Idempotency-Key`.

### 3.5 Асинхронность

- API возвращает результат синхронно.
- События публикуются асинхронно после ответа.

### 3.6 Агрегация на сервере

Сервер собирает данные для экранов Front за один запрос.

**Зачем:**
- меньше сетевых задержек;
- нет N+1 запросов;
- данные консистентны;
- меньше трафика.

### 3.7 Разделение чтения и записи

- **Запись** — обычные эндпоинты.
- **Чтение** — агрегированные «view» эндпоинты.

### 3.8 Только идентификаторы

API отдаёт **идентификаторы**, а не отображаемые имена.

**Не отдаётся:** `displayName`, `organizationDisplayName`, `resolvedRoleDisplayName`, `statusDisplayName`.

### 3.9 Расширяемость

- Минорные изменения — additive.
- Мажорные — новая версия.

---

## 4. Соглашения и формат

### 4.1 Базовый URL

```
https://api.example.com/approval/v1
```

### 4.2 Формат данных

- JSON (UTF-8).
- Даты — ISO 8601 UTC.
- Идентификаторы — UUID v4.
- Enum — UPPER_SNAKE_CASE.

### 4.3 Стандартные заголовки

**Запрос:**

| Заголовок | Обязательный | Описание |
|---|---|---|
| `Authorization` | Да | Bearer-токен |
| `Content-Type` | Для POST/PUT/PATCH | application/json |
| `Accept` | Нет | application/json |
| `Idempotency-Key` | Для POST | UUID |
| `If-Match` | Опционально | ETag |
| `If-None-Match` | Опционально | ETag |
| `X-Correlation-Id` | Опционально | Трассировка |

**Ответ:**

| Заголовок | Описание |
|---|---|
| `Content-Type` | application/json |
| `ETag` | Версия ресурса |
| `Cache-Control` | Политика кэширования |
| `X-Correlation-Id` | Эхо |

### 4.4 Формат ответа

**Успешный:**

```json
{
  "data": { ... },
  "meta": { "correlationId": "..." }
}
```

**Список:**

```json
{
  "data": [ ... ],
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8
  }
}
```

**Ошибка:**

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Обязательные слоты не заполнены",
    "details": [
      {
        "field": "stages[0].actorSlots[1].userId",
        "issue": "Обязательный слот не заполнен"
      }
    ]
  },
  "meta": { "correlationId": "..." }
}
```

### 4.5 Формат дат

- ISO 8601 UTC: `2026-09-15T10:30:00Z`.
- Сроки — в рабочих днях.

### 4.6 Формат идентификаторов

UUID v4:
```
7c9e6679-7425-40de-944b-e07fc1f90ae7
```

---

## 5. Аутентификация и авторизация

### 5.1 Аутентификация

**JWT Bearer Token.**

```
Authorization: Bearer <token>
```

### 5.2 Авторизация

**Уровень API (RBAC):**
- право на эндпоинт.

**Уровень ресурса (ABAC):**
- право на конкретный ресурс.

**Примеры:**

| Эндпоинт | RBAC | ABAC |
|---|---|---|
| `POST /processes/{id}/start` | Initiator | `initiatorId == user.id` |
| `POST /processes/{id}/decisions` | Approver | `participant.userId == user.id` |
| `POST /configs` | Admin | — |
| `PUT /admin/notification-settings` | Admin | — |

### 5.3 Ошибки авторизации

| Код | Описание |
|---|---|
| 401 | Токен отсутствует или недействителен |
| 403 | Нет прав на ресурс |

---

## 6. Версионирование

### 6.1 Версия в URL

```
/approval/v1/...
/approval/v2/...
```

### 6.2 Политика поддержки

- Одновременно 2 последние мажорные версии.
- Deprecated за 6 месяцев до удаления.

### 6.3 Изменения

| Тип | Пример | Версия |
|---|---|---|
| Additive | Новое поле | Minor (v1.1) |
| Additive | Новый опциональный параметр | Minor |
| Breaking | Удаление поля | Major (v2.0) |
| Breaking | Изменение типа | Major |

---

## 7. Идемпотентность

### 7.1 Механизм

**Заголовок:** `Idempotency-Key` — UUID.

**Логика:**
1. Клиент отправляет POST с ключом.
2. Сервер проверяет, был ли такой ключ.
3. Если был — возвращает сохранённый ответ.
4. Если нет — выполняет операцию и сохраняет результат.

### 7.2 Срок хранения ключа

24 часа. Реализовано таблицей `idempotency_key` (`key`, `request_hash`, `response_status`, `response_body`, `created_at`, `expires_at`) — см. `08_db_schema.md` §20, ADR-026.

### 7.3 Пример

```
POST /approval/v1/processes/{id}/start
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

### 7.4 Ошибки

| Код | Описание |
|---|---|
| 409 Conflict | `Idempotency-Key` уже использован с другими данными (`request_hash` не совпадает) |

---

## 8. Пагинация, фильтрация, сортировка

### 8.1 Пагинация

| Параметр | По умолчанию |
|---|---|
| `page` | 0 |
| `size` | 20 |
| `sort` | `createdAt,desc` |

**Ответ:**

```json
{
  "data": [ ... ],
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8
  }
}
```

### 8.2 Фильтрация

**Операторы:**
- `eq` — равенство
- `ne` — не равно
- `gt`, `gte`, `lt`, `lte` — сравнение
- `in` — вхождение в список
- `like` — подстрока

**Пример:**
```
GET /processes?status.in=IN_PROGRESS,ON_REWORK&createdAt.gte=2026-01-01
```

### 8.3 Сортировка

```
GET /processes?sort=createdAt,desc&sort=status,asc
```

---

## 9. Обработка ошибок

### 9.1 Формат

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Обязательные слоты не заполнены",
    "details": [ ... ]
  },
  "meta": { "correlationId": "..." }
}
```

### 9.2 Коды ошибок

| Код | HTTP | Описание |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Ошибка валидации |
| `UNAUTHORIZED` | 401 | Не аутентифицирован |
| `FORBIDDEN` | 403 | Нет прав |
| `NOT_FOUND` | 404 | Не найден |
| `CONFLICT` | 409 | Конфликт |
| `IDEMPOTENCY_CONFLICT` | 409 | Дубль Idempotency-Key |
| `BUSINESS_ERROR` | 422 | Бизнес-ошибка |
| `INTERNAL_ERROR` | 500 | Внутренняя ошибка |

### 9.3 Бизнес-ошибки

| Код | Описание |
|---|---|
| `PROCESS_ALREADY_STARTED` | Процесс уже запущен |
| `PROCESS_NOT_IN_DRAFT` | Процесс не в черновике |
| `MANDATORY_SLOTS_NOT_FILLED` | Обязательные слоты не заполнены |
| `DURATION_INVALID` | Некорректный срок |
| `ACTIVE_REMARKS_PRESENT` | Есть необработанные замечания |
| `PARTICIPANT_NOT_ASSIGNED` | Участник не назначен |
| `DECISION_ALREADY_RECORDED` | Решение уже принято |
| `REMARK_NOT_ACTIVE` | Замечание не в статусе Active |
| `COMMENT_REQUIRED` | Требуется комментарий |
| `REASON_REQUIRED` | Требуется обоснование |
| `PROCESS_ARCHIVED` | Процесс в архиве |
| `PROCESS_NOT_ARCHIVED` | Процесс не в архиве |
| `INVALID_RETURN_TARGET` | Некорректная цель возврата |
| `REVISION_LABEL_DUPLICATE` | Метка ревизии уже существует |
| `REVISION_NOT_ALLOWED` | Ревизии запрещены шаблоном |
| `REVISION_LABEL_EMPTY` | Метка ревизии пустая |
| `REMINDER_INTERVAL_INVALID` | `reminderIntervalHours` ≤ 0 |

---

## 10. Группа 1: Шаблоны маршрутов

### 10.1 GET /templates — Список шаблонов

**Параметры:**
- `status` — фильтр
- `processType` — фильтр
- `name` — поиск
- `page`, `size`, `sort`

**Ответ 200:**

```json
{
  "data": [
    {
      "id": "d3d94468-...",
      "name": "Согласование договора поставки",
      "processType": "STANDARD",
      "status": "PUBLISHED",
      "version": 3,
      "parentTemplateId": "c2c83357-...",
      "processIterationEnabled": true,
      "createdAt": "2026-01-01T10:00:00Z",
      "publishedAt": "2026-01-05T10:00:00Z"
    }
  ],
  "meta": { ... }
}
```

**Изменено (ADR-023):** `id` указывает на конкретную версионную строку `Template`; `version`/`parentTemplateId` — цепочка версий/форка (нет отдельной сущности «TemplateVersion»).

### 10.2 GET /templates/{id} — Шаблон по ID

**Ответ 200:**

```json
{
  "data": {
    "id": "d3d94468-...",
    "name": "Согласование договора поставки",
    "processType": "STANDARD",
    "status": "PUBLISHED",
    "version": 3,
    "parentTemplateId": "c2c83357-...",
    "processIterationEnabled": true,
    "responsibleRoles": null,
    "requiresResponsibleApproval": false,
    "applicabilityRules": [
      {
        "entityTypes": ["Document"],
        "entitySubtypes": ["SupplyContract"],
        "attributeConditions": { "amount": { "gte": 1000000 } }
      }
    ],
    "stages": [
      {
        "orderIdx": 1,
        "name": "Проверка",
        "description": "Первичная проверка документа",
        "stageType": "APPROVAL",
        "duration": 5,
        "decisionMode": "AND",
        "executionOrder": "PARALLEL",
        "isMandatory": true,
        "isOrderMandatory": true,
        "allowedReturnStages": [1],
        "actorSlots": [
          {
            "slotType": "ACTOR",
            "orderIdx": 1,
            "userId": null,
            "organizationId": "org-1",
            "acceptableRoles": ["role-manager"],
            "required": true,
            "isUserEditable": true,
            "isOrganizationEditable": false,
            "isDeletable": false,
            "additionalApprovers": [
              {
                "slotType": "ADDITIONAL_APPROVER",
                "userId": "user-10",
                "organizationId": "org-1",
                "required": false,
                "isUserEditable": true,
                "isOrganizationEditable": false,
                "isDeletable": true,
                "dueOffset": "H3"
              }
            ]
          }
        ]
      }
    ],
    "createdAt": "2026-01-01T10:00:00Z",
    "publishedAt": "2026-01-05T10:00:00Z"
  }
}
```

### 10.3 POST /templates — Создать шаблон

**Тело запроса:**

```json
{
  "name": "Согласование договора поставки",
  "processType": "STANDARD",
  "processIterationEnabled": true,
  "applicabilityRules": [ ... ],
  "stages": [ ... ]
}
```

**Ответ 201.**

### 10.4 PUT /templates/{id} — Обновить шаблон

**Условие:** статус `DRAFT`.

**Ответ 200.**

### 10.5 POST /templates/{id}/publish — Опубликовать

**Валидации:**
- Все обязательные слоты заполнены.
- Все сроки > 0.
- Обязательные этапы имеют слоты.
- `allowedReturnStages` ⊆ прошлых и текущего.

**Ответ 200.**

### 10.6 POST /templates/{id}/fork — Форк

**Описание (ADR-023).** Создаёт новую строку `Template` с новым `id`, `version = текущая + 1` и `parentTemplateId = id` исходного шаблона. Это тот же механизм, которым публикация изменения уже опубликованного шаблона создаёт новую версию — отдельной сущности «TemplateVersion» нет.

**Ответ 201:** новая версия (новая строка `Template`) в статусе `DRAFT`.

### 10.7 POST /templates/{id}/deprecate — Депрекация

**Условие:** статус `PUBLISHED`, нет активных процессов.

**Ответ 200.**

### 10.8 POST /templates/{id}/archive — Архивировать

**Условие:** статус `DEPRECATED`.

**Ответ 200.**

---

## 11. Группа 2: Подбор шаблона

### 11.1 POST /templates/match — Подбор шаблонов

**Тело запроса:**

```json
{
  "entityRef": {
    "entityType": "Document",
    "entitySubtype": "SupplyContract",
    "entityId": "f47ac10b-..."
  },
  "attributes": {
    "amount": 1500000,
    "department": "IT",
    "priority": "HIGH"
  }
}
```

**Ответ 200:**

```json
{
  "data": [
    {
      "templateId": "d3d94468-...",
      "name": "Согласование договора поставки",
      "processType": "STANDARD",
      "matchScore": 100,
      "matchedRules": [
        {
          "ruleIndex": 0,
          "description": "Тип Document + подтип SupplyContract + сумма ≥ 1 млн"
        }
      ]
    }
  ]
}
```

**Изменено (ADR-023):** поле `templateVersion` убрано — `templateId` уже указывает на конкретную версионную строку `Template`, отдельный номер версии избыточен.

---

## 12. Группа 3: Процессы

### 12.1 POST /processes — Создать процесс

**Тело запроса:**

```json
{
  "entityRef": {
    "entityType": "Document",
    "entitySubtype": "SupplyContract",
    "entityId": "f47ac10b-..."
  },
  "templateId": "d3d94468-...",
  "route": {
    "stages": [
      {
        "orderIdx": 1,
        "name": "Проверка",
        "duration": 5,
        "actorSlots": [
          {
            "orderIdx": 1,
            "userId": "user-1",
            "organizationId": "org-1",
            "additionalApprovers": [
              { "userId": "user-10", "dueOffset": "H3" }
            ]
          }
        ]
      }
    ]
  }
}
```

**Изменено (ADR-023):** поле `templateVersion` убрано из тела запроса — `templateId` уже идентифицирует конкретную версию `Template`, которая становится `ProcessInstance.templateRef`.

**Ответ 201:**

```json
{
  "data": {
    "processId": "7c9e6679-...",
    "status": "DRAFT",
    "processType": "STANDARD",
    "createdAt": "2026-09-15T10:30:00Z"
  }
}
```

### 12.2 GET /processes — Список процессов

**Параметры:**
- `status`
- `processType`
- `initiatorId`
- `entityType`, `entitySubtype`, `entityId`
- `documentAggregateId`
- `includeArchived`
- `page`, `size`, `sort`

**Ответ 200:**

```json
{
  "data": [
    {
      "processId": "7c9e6679-...",
      "entityRef": { ... },
      "documentAggregateId": "agg-...",
      "revisionLabel": "Изм. 1",
      "processType": "STANDARD",
      "status": "IN_PROGRESS",
      "initiatorId": "user-1",
      "createdAt": "2026-09-15T10:30:00Z",
      "startedAt": "2026-09-15T10:30:00Z"
    }
  ],
  "meta": { ... }
}
```

### 12.3 GET /processes/{id} — Процесс по ID

**Ответ 200:**

```json
{
  "data": {
    "processId": "7c9e6679-...",
    "entityRef": { ... },
    "documentAggregateId": "agg-...",
    "revisionLabel": "Изм. 1",
    "parentProcessId": null,
    "templateRef": "d3d94468-...",
    "processType": "STANDARD",
    "configVersion": 3,
    "status": "IN_PROGRESS",
    "initiatorId": "user-1",
    "responsibleUserId": null,
    "createdAt": "2026-09-15T10:30:00Z",
    "startedAt": "2026-09-15T10:30:00Z"
  }
}
```

**Изменено (ADR-023):** поле `templateVersion` убрано — `templateRef` указывает на конкретную версионную строку `Template`; версия зафиксирована самой ссылкой (Snapshot-on-Start, см. `11_adr.md` ADR-003, ADR-023).

### 12.4 POST /processes/{id}/start — Запустить процесс

**Условие:** пользователь — инициатор; статус `DRAFT`; обязательные слоты заполнены.

**Ответ 200:**

```json
{
  "data": {
    "processId": "7c9e6679-...",
    "status": "IN_PROGRESS",
    "startedAt": "2026-09-15T10:30:00Z"
  }
}
```

### 12.5 POST /processes/{id}/submit — Отправить маршрут ответственному

**Условие:** UNIFIED; статус `DRAFT`; пользователь — инициатор.

**Ответ 200:**

```json
{
  "data": {
    "processId": "7c9e6679-...",
    "status": "PENDING_RESPONSIBLE_APPROVAL",
    "submittedAt": "2026-09-15T10:30:00Z"
  }
}
```

### 12.6 POST /processes/{id}/approve-route — Согласовать маршрут

**Условие:** UNIFIED; статус `PENDING_RESPONSIBLE_APPROVAL`; пользователь — ответственный.

**Ответ 200.**

### 12.7 POST /processes/{id}/reject-route — Отклонить маршрут

**Тело запроса:**

```json
{
  "comment": "Нужен другой маршрут"
}
```

**Ответ 200.**

### 12.8 POST /processes/{id}/recall — Отозвать

**Тело запроса:**

```json
{
  "reason": "Обнаружены ошибки"
}
```

**Ответ 200:**

```json
{
  "data": {
    "processId": "7c9e6679-...",
    "status": "RECALLED",
    "recalledAt": "2026-09-15T11:00:00Z"
  }
}
```

### 12.9 POST /processes/{id}/resume — Возобновить

**Описание.** Возобновление процесса после отзыва или доработки.

**Тело запроса:**

```json
{
  "targetStageId": "11111111-...",
  "comment": "Возврат на этап 1"
}
```

**Валидации:**
- `targetStageId` ∈ `allowedReturnStages` текущего этапа.
- Все замечания обработаны.

**Ответ 200:**

```json
{
  "data": {
    "processId": "7c9e6679-...",
    "status": "IN_PROGRESS",
    "targetStageId": "11111111-...",
    "newIterationIdx": 2,
    "stagesReset": [2, 3]
  }
}
```

---

## 13. Группа 4: Этапы и итерации

### 13.1 GET /processes/{id}/stages — Этапы процесса

**Ответ 200:**

```json
{
  "data": [
    {
      "stageId": "8a7b6c5d-...",
      "orderIdx": 1,
      "name": "Проверка",
      "description": "Первичная проверка",
      "stageType": "APPROVAL",
      "status": "ACTIVE",
      "duration": 5,
      "startedAt": "2026-09-15T10:31:00Z",
      "dueAt": "2026-09-20T10:31:00Z",
      "currentIterationIdx": 2,
      "allowedReturnStages": [1]
    }
  ]
}
```

### 13.2 GET /processes/{id}/stages/{stageId} — Этап по ID

**Ответ 200:** полное описание этапа с итерациями и участниками.

### 13.3 GET /processes/{id}/stages/{stageId}/iterations — Итерации этапа

**Ответ 200:**

```json
{
  "data": [
    {
      "iterationIdx": 1,
      "status": "SUPERSEDED",
      "startedAt": "2026-09-15T10:31:00Z",
      "completedAt": "2026-09-16T12:00:00Z",
      "summary": {
        "participantsCount": 2,
        "decisionsCount": 2
      }
    },
    {
      "iterationIdx": 2,
      "status": "ACTIVE",
      "startedAt": "2026-09-17T10:00:00Z",
      "completedAt": null,
      "participants": [ ... ]
    }
  ]
}
```

### 13.4 GET /processes/{id}/stages/{stageId}/iterations/{iterIdx} — Итерация

**Ответ 200:** участники, решения, статусы.

---

## 14. Группа 5: Участники

### 14.1 GET /processes/{id}/stages/{stageId}/participants — Участники

**Ответ 200:**

```json
{
  "data": [
    {
      "participantId": "11111111-...",
      "userId": "user-1",
      "organizationId": "org-1",
      "role": "APPROVER",
      "status": "ASSIGNED",
      "orderIdx": 0,
      "decision": null,
      "isUserEditable": true,
      "isOrganizationEditable": false,
      "isDeletable": false
    }
  ]
}
```

**Изменено (ADR-022):** добавлено `orderIdx` — порядок работы участника при `executionOrder = Sequential` (см. `03_domain_model.md` §7.6). `status` может быть также `PENDING` (при `Sequential`, до назначения задачи).

### 14.2 PATCH /processes/{id}/stages/{stageId}/participants/{participantId} — Изменить участника

**Условие:** флаги редактирования позволяют; этап не взят в работу.

**Тело запроса:**

```json
{
  "userId": "user-2",
  "organizationId": "org-2"
}
```

**Ответ 200.**

### 14.3 DELETE /processes/{id}/stages/{stageId}/participants/{participantId} — Удалить участника

**Условие:** `isDeletable = true`; `required = false`.

**Ответ 204.**

### 14.4 POST /processes/{id}/stages/{stageId}/participants — Добавить участника

**Тело запроса:**

```json
{
  "userId": "user-3",
  "organizationId": "org-3",
  "actorSlotRef": "...",
  "role": "APPROVER"
}
```

**Ответ 201.**

---

## 15. Группа 6: Дополнительные согласующие

### 15.1 POST /processes/{id}/stages/{stageId}/participants/{participantId}/additional-approvers — Добавить

**Описание.** Добавляет доп. согласующего к основному.

**Тело запроса:**

```json
{
  "userId": "user-10",
  "organizationId": "org-1",
  "dueOffset": "H3"
}
```

**Валидации:**
- Срок доп. ≤ срока основного.
- Пользователь не дублируется в иерархии.

**Ответ 201:**

```json
{
  "data": {
    "additionalApproverId": "33333333-...",
    "participantId": "11111111-...",
    "parentAdditionalApproverId": null,
    "userId": "user-10",
    "organizationId": "org-1",
    "assignedBy": "PARTICIPANT",
    "level": 1,
    "dueAt": "2026-09-20T07:31:00Z",
    "dueOffset": "H3",
    "status": "ASSIGNED"
  }
}
```

### 15.2 POST /processes/{id}/stages/{stageId}/additional-approvers/{aaId}/children — Добавить дочернего

**Описание.** Доп. согласующий добавляет другого доп. согласующего.

**Тело запроса:**

```json
{
  "userId": "user-11",
  "organizationId": "org-1",
  "dueOffset": "H3"
}
```

**Валидации:**
- `aaId` — существующий доп. согласующий.
- Срок ребёнка ≤ срока родителя.

**Ответ 201:**

```json
{
  "data": {
    "additionalApproverId": "44444444-...",
    "participantId": "11111111-...",
    "parentAdditionalApproverId": "33333333-...",
    "userId": "user-11",
    "organizationId": "org-1",
    "assignedBy": "ADDITIONAL_APPROVER",
    "level": 2,
    "dueAt": "2026-09-20T07:31:00Z",
    "dueOffset": "H3",
    "status": "ASSIGNED"
  }
}
```

### 15.3 GET /processes/{id}/stages/{stageId}/additional-approvers — Дерево доп. согласующих

**Ответ 200:**

```json
{
  "data": [
    {
      "additionalApproverId": "33333333-...",
      "participantId": "11111111-...",
      "parentAdditionalApproverId": null,
      "userId": "user-10",
      "organizationId": "org-1",
      "assignedBy": "PARTICIPANT",
      "level": 1,
      "status": "RECOMMENDED",
      "recommendation": "REJECT",
      "children": [
        {
          "additionalApproverId": "44444444-...",
          "parentAdditionalApproverId": "33333333-...",
          "userId": "user-11",
          "level": 2,
          "status": "IN_PROGRESS",
          "recommendation": null,
          "children": []
        }
      ]
    }
  ]
}
```

### 15.4 DELETE /processes/{id}/stages/{stageId}/additional-approvers/{aaId} — Удалить

**Условие:** процесс в статусе `RECALLED` или `ON_REWORK`; `isDeletable = true`.

**Ответ 204.**

### 15.5 POST /processes/{id}/stages/{stageId}/additional-approvers/{aaId}/decision — Дать рекомендацию

**Примечание (ADR-016).** Эта рекомендация пишется в `additional_approver.recommendation` — не в таблицу `decision`. Это отдельный, информационный механизм, не унифицированный с решением основного согласующего (см. §16.1).

**Тело запроса:**

```json
{
  "result": "REJECT",
  "comment": "Не вижу оснований"
}
```

**Ответ 200.**

---

## 16. Группа 7: Решения

### 16.1 POST /processes/{id}/stages/{stageId}/decisions — Принять решение

**Описание.** Решение **основного** участника (`Participant`). Управляет ходом процесса — см. `evaluateAggregation(stage)` (`05_guards_actions_registry.md` §4.2, ADR-022). Не унифицировано с рекомендацией доп. согласующего (§15.5, ADR-016).

**Тело запроса:**

```json
{
  "decision": "APPROVE_WITH_COMMENTS",
  "comment": "Нужно уточнить раздел 3"
}
```

**Валидации:**
- `APPROVE_WITH_COMMENTS` требует комментарий.
- `REJECT` требует комментарий или замечание.
- Участник должен быть назначен.

**Ответ 200:**

```json
{
  "data": {
    "decisionId": "66666666-...",
    "participantId": "11111111-...",
    "result": "APPROVE_WITH_COMMENTS",
    "comment": "Нужно уточнить раздел 3",
    "auto": false,
    "recordedAt": "2026-09-16T14:00:00Z"
  }
}
```

### 16.2 GET /processes/{id}/stages/{stageId}/decisions — Решения этапа

**Ответ 200:** список решений.

---

## 17. Группа 8: Замечания

### 17.1 POST /processes/{id}/remarks — Создать замечание

**Тело запроса:**

```json
{
  "stageId": "8a7b6c5d-...",
  "text": "Требуется уточнить сроки",
  "attachments": ["file-1"]
}
```

**Условие:** автор — основной (Active) или доп. (Proposed).

**Ответ 201:**

```json
{
  "data": {
    "remarkId": "44444444-...",
    "status": "ACTIVE",
    "createdAt": "2026-09-16T14:00:00Z"
  }
}
```

### 17.2 GET /processes/{id}/remarks — Список замечаний

**Параметры:**
- `status`
- `authorId`
- `stageId`
- `page`, `size`

**Ответ 200.**

### 17.3 GET /processes/{id}/remarks/{remarkId} — Замечание по ID

**Ответ 200.**

### 17.4 POST /processes/{id}/remarks/{remarkId}/activate — Активировать

**Условие:** статус `PROPOSED`; пользователь — основной; решение не принято.

**Ответ 200.**

### 17.5 POST /processes/{id}/remarks/{remarkId}/mark-not-required — Пометить «не требуется»

**Условие:** статус `PROPOSED`; пользователь — основной.

**Ответ 200.**

### 17.6 POST /processes/{id}/remarks/{remarkId}/take-into-work — Взять в работу

**Условие:** статус `ACTIVE`; пользователь — инициатор.

**Ответ 200.**

### 17.7 POST /processes/{id}/remarks/{remarkId}/resolve — Исправить

**Условие:** статус `ACTIVE` или `IN_PROGRESS`; пользователь — инициатор.

**Тело запроса:**

```json
{
  "comment": "Исправлено"
}
```

**Ответ 200.**

### 17.8 POST /processes/{id}/remarks/{remarkId}/reject — Отклонить

**Условие:** статус `ACTIVE` или `IN_PROGRESS`; пользователь — инициатор.

**Тело запроса:**

```json
{
  "reason": "Не относится к документу"
}
```

**Ответ 200.**

### 17.9 GET /processes/{id}/remarks/report — Отчёт по замечаниям

**Ответ 200:**

```json
{
  "data": {
    "processId": "7c9e6679-...",
    "summary": {
      "total": 10,
      "active": 2,
      "inProgress": 0,
      "resolved": 5,
      "rejected": 1,
      "notRequired": 1,
      "autoProcessed": 1,
      "closed": 0
    },
    "remarks": [ ... ]
  }
}
```

---

## 18. Группа 9: Комментарии

### 18.1 POST /processes/{id}/comments — Создать комментарий

**Тело запроса:**

```json
{
  "stageId": "8a7b6c5d-...",
  "text": "Уточните, пожалуйста",
  "parentId": null,
  "attachments": []
}
```

**Ответ 201.**

### 18.2 GET /processes/{id}/comments — Список комментариев

**Ответ 200.**

---

## 19. Группа 10: Возврат на этап

### 19.1 GET /processes/{id}/stages/{stageId}/return-targets — Доступные цели возврата

**Описание.** Возвращает список этапов, на которые можно вернуть документ при возобновлении.

**Ответ 200:**

```json
{
  "data": {
    "currentStageId": "9b8c7d6e-...",
    "currentStageOrderIdx": 3,
    "allowedTargets": [
      {
        "stageId": "11111111-...",
        "orderIdx": 1,
        "name": "Проверка",
        "isCurrent": false
      },
      {
        "stageId": "8a7b6c5d-...",
        "orderIdx": 2,
        "name": "Согласование",
        "isCurrent": false
      },
      {
        "stageId": "9b8c7d6e-...",
        "orderIdx": 3,
        "name": "Утверждение",
        "isCurrent": true
      }
    ],
    "requiresUserChoice": true
  }
}
```

**Зачем.** UI показывает модалку выбора этапа, если `requiresUserChoice = true`.

---

## 20. Группа 11: Ревизии документа

### 20.1 POST /processes/revision — Создать ревизию

**Описание.** Создаёт новый процесс для новой ревизии документа.

**Тело запроса:**

```json
{
  "documentAggregateId": "agg-11111111-...",
  "newEntityRef": {
    "entityType": "Document",
    "entitySubtype": "PD",
    "entityId": "new-doc-id"
  },
  "revisionLabel": "Изм. 2",
  "templateId": "d3d94468-...",
  "interruptPrevious": true,
  "comment": "Серьёзные изменения по разделу 3"
}
```

**Валидации:**
- `documentAggregateId` существует.
- `revisionLabel` не пустой, уникален в агрегате.
- `templateId` поддерживает ревизии (`processIterationEnabled = true`).
- Предыдущий процесс не в статусе `ARCHIVED`.

**Ответ 201:**

```json
{
  "data": {
    "processId": "7c9e6679-...",
    "documentAggregateId": "agg-11111111-...",
    "revisionLabel": "Изм. 2",
    "parentProcessId": "aaaaaaaa-...",
    "status": "DRAFT",
    "previousProcessStatus": "RECALLED",
    "createdAt": "2026-09-15T10:30:00Z"
  }
}
```

### 20.2 GET /entities/aggregate/{documentAggregateId}/revisions — Список ревизий агрегата

**Ответ 200:**

```json
{
  "data": {
    "documentAggregateId": "agg-11111111-...",
    "revisions": [
      {
        "revisionLabel": "Изм. 1",
        "processId": "...",
        "status": "RECALLED",
        "isActive": false,
        "createdAt": "2026-01-15T10:00:00Z"
      },
      {
        "revisionLabel": "Изм. 2",
        "processId": "...",
        "status": "IN_PROGRESS",
        "isActive": true,
        "createdAt": "2026-09-15T10:30:00Z"
      }
    ]
  }
}
```

---

## 21. Группа 12: Финальное решение (UNIFIED)

### 21.1 POST /processes/{id}/final-decision — Принять финальное решение

**Условие:** статус `AWAITING_FINAL_DECISION`; пользователь — ответственный.

**Тело запроса:**

```json
{
  "result": "APPROVE",
  "comment": "Согласовано"
}
```

**Ответ 200:**

```json
{
  "data": {
    "finalDecisionId": "77777777-...",
    "result": "APPROVE",
    "comment": "Согласовано",
    "recordedAt": "2026-09-18T12:00:00Z"
  }
}
```

### 21.2 GET /processes/{id}/final-decision — Получить финальное решение

**Ответ 200.**

---

## 22. Группа 13: Архивация

### 22.1 GET /processes/archived — Список архивных процессов

**Параметры:** пагинация, фильтры.

**Ответ 200.**

### 22.2 POST /processes/{id}/restore — Восстановить из архива

**Условие:** пользователь — администратор; статус `ARCHIVED`.

**Ответ 200:**

```json
{
  "data": {
    "processId": "7c9e6679-...",
    "status": "APPROVED",
    "restoredAt": "2027-04-15T10:00:00Z",
    "restoredBy": "admin-user",
    "newAutoArchiveScheduledAt": "2027-10-12T10:00:00Z"
  }
}
```

---

## 23. Группа 14: Конфигурация карт переходов

### 23.1 GET /configs — Список конфигов

**Параметры:**
- `entityType`
- `processType`
- `status`

**Ответ 200.**

### 23.2 GET /configs/{id} — Конфиг по ID

**Ответ 200:** полный конфиг с состояниями и переходами.

### 23.3 POST /configs — Создать конфиг

**Тело запроса:**

```json
{
  "entityType": "ProcessInstance",
  "processType": "STANDARD",
  "states": [ ... ],
  "transitions": [ ... ]
}
```

**Ответ 201.**

### 23.4 PUT /configs/{id} — Обновить конфиг

**Условие:** статус `DRAFT`.

**Ответ 200.**

### 23.5 POST /configs/{id}/publish — Опубликовать

**Валидации:**
- Ровно одно `isInitial`.
- Все from/to существуют.
- Все guards/actions зарегистрированы.
- Нет висячих состояний.

**Ответ 200.**

### 23.6 POST /configs/{id}/deprecate — Депрекация

**Условие:** `activeProcessCount = 0`.

**Ответ 200.**

### 23.7 POST /configs/{id}/archive — Архивировать

**Ответ 200.**

### 23.8 GET /configs/{id}/audit — Аудит изменений

**Ответ 200.**

---

## 24. Группа 15: Реестр guards и actions

### 24.1 GET /registry/guards — Список guards

**Ответ 200:**

```json
{
  "data": [
    {
      "code": "IsInitiator",
      "displayName": "Является инициатором",
      "description": "Проверяет, что текущий пользователь — инициатор процесса",
      "scope": "GLOBAL",
      "categories": ["AUTHORIZATION"],
      "paramsSchema": null
    }
  ]
}
```

### 24.2 GET /registry/guards/{code} — Guard по коду

**Ответ 200.**

### 24.3 GET /registry/actions — Список actions

**Ответ 200.**

### 24.4 GET /registry/actions/{code} — Action по коду

**Ответ 200.**

---

## 25. Группа 16: Агрегированные эндпоинты для Front

### 25.1 Назначение

Группа эндпоинтов для интерфейса. Возвращают **готовые модели** для экранов, чтобы Front не делал N+1 запросов.

**Принципы:**
- Агрегация на сервере.
- Идентификация по сущности.
- Разные ответы под разные роли.
- Секция `actions` — единый источник истины.
- Только идентификаторы.

### 25.2 GET /entities/approval-view — Агрегированное представление

**Параметры:**

| Параметр | Тип | Обязательный | По умолчанию |
|---|---|---|---|
| `entityType` | String | Да | — |
| `entitySubtype` | String | Нет | — |
| `entityId` | UUID | Да | — |
| `detailLevel` | Enum | Нет | STANDARD |
| `full` | Boolean | Нет | false |
| `includeHistory` | Boolean | Нет | false |
| `role` | Enum | Нет | auto |

**Логика выбора процесса:**

| Ситуация | Что возвращается |
|---|---|
| Активный процесс | Активный |
| Только завершённые | Последний завершённый |
| Нет процессов | 404 |

**Ответ 200:**

```json
{
  "data": {
    "process": {
      "processId": "7c9e6679-...",
      "entityRef": { ... },
      "documentAggregateId": "agg-...",
      "revisionLabel": "Изм. 2",
      "processType": "STANDARD",
      "status": "IN_PROGRESS",
      "initiatorId": "user-1",
      "responsibleUserId": null,
      "createdAt": "2026-09-15T10:30:00Z",
      "startedAt": "2026-09-15T10:30:00Z",
      "completedAt": null,
      "archivedAt": null
    },
    "processIterations": [
      {
        "iterationIdx": 1,
        "status": "SUPERSEDED",
        "startedAt": "2026-09-15T10:30:00Z",
        "completedAt": "2026-09-16T12:00:00Z",
        "summary": {
          "stagesCount": 3,
          "approved": 1,
          "rejected": 2,
          "participantsCount": 5
        }
      },
      {
        "iterationIdx": 2,
        "status": "ACTIVE",
        "startedAt": "2026-09-17T10:00:00Z",
        "completedAt": null,
        "stages": [
          {
            "stageId": "8a7b6c5d-...",
            "orderIdx": 1,
            "name": "Проверка",
            "description": "Первичная проверка",
            "stageType": "APPROVAL",
            "status": "ACTIVE",
            "duration": 5,
            "startedAt": "2026-09-17T10:01:00Z",
            "dueAt": "2026-09-22T10:01:00Z",
            "completedAt": null,
            "currentIterationIdx": 1,
            "allowedReturnStages": [1],
            "iterations": [
              {
                "iterationIdx": 1,
                "status": "ACTIVE",
                "startedAt": "2026-09-17T10:01:00Z",
                "completedAt": null,
                "participants": [
                  {
                    "participantId": "11111111-...",
                    "userId": "user-1",
                    "organizationId": "org-1",
                    "role": "APPROVER",
                    "status": "DECIDED",
                    "decision": {
                      "result": "APPROVE",
                      "comment": null,
                      "auto": false,
                      "recordedAt": "2026-09-18T14:00:00Z"
                    },
                    "additionalApprovers": [
                      {
                        "additionalApproverId": "33333333-...",
                        "parentAdditionalApproverId": null,
                        "userId": "user-10",
                        "organizationId": "org-1",
                        "assignedBy": "PARTICIPANT",
                        "level": 1,
                        "status": "RECOMMENDED",
                        "decision": {
                          "result": "REJECT",
                          "comment": "Не вижу оснований",
                          "auto": false,
                          "recordedAt": "2026-09-18T15:00:00Z"
                        }
                      }
                    ]
                  }
                ]
              }
            ]
          }
        ]
      }
    ],
    "actions": { ... }
  },
  "meta": {
    "role": "INITIATOR",
    "detailLevel": "STANDARD",
    "full": false,
    "correlationId": "..."
  }
}
```

**Параметр `full`:**
- `false` — последняя итерация развёрнута, предыдущие свёрнуты (только summary).
- `true` — все итерации развёрнуты.

### 25.3 GET /entities/approval-actions — Только действия

**Зачем.** Точечный запрос после действий или поллинга.

**Параметры:** те же, что у `approval-view` (без `full`, `includeHistory`).

**Ответ 200:**

```json
{
  "data": {
    "canStart": false,
    "canRecall": true,
    "canResume": false,
    "canEditRoute": true,
    "canDecide": false,
    "canAddAdditionalApprover": false,
    "canCreateRemark": false,
    "canTakeRemarkIntoWork": true,
    "canResolveRemark": true,
    "canRejectRemark": true,
    "canChooseReturnTarget": false,
    "availableReturnTargets": [],
    "canCreateRevision": true,
    "canFinalDecision": false
  }
}
```

### 25.4 GET /entities/approval-remarks — Замечания и комментарии

**Описание.** Единая ручка для получения всех замечаний и комментариев сущности.

**Параметры:**

| Параметр | Тип | Обязательный | По умолчанию |
|---|---|---|---|
| `entityType` | String | Да | — |
| `entitySubtype` | String | Нет | — |
| `entityId` | UUID | Да | — |
| `type` | Enum | Нет | ALL |
| `status` | List | Нет | все |
| `stageId` | UUID | Нет | — |
| `authorId` | UUID | Нет | — |
| `page`, `size` | Int | Нет | 0, 50 |

**Ответ 200:**

```json
{
  "data": [
    {
      "id": "44444444-...",
      "type": "REMARK",
      "status": "ACTIVE",
      "text": "Требуется уточнить раздел 3",
      "author": {
        "userId": "user-1",
        "role": "APPROVER"
      },
      "stageId": "8a7b6c5d-...",
      "stageName": "Проверка",
      "stageIterationIdx": 1,
      "participantId": "11111111-...",
      "createdAt": "2026-09-16T14:00:00Z",
      "activatedAt": "2026-09-16T15:00:00Z",
      "resolvedAt": null,
      "attachments": [
        { "fileId": "file-1", "fileName": "comment.pdf" }
      ]
    },
    {
      "id": "55555555-...",
      "type": "COMMENT",
      "status": null,
      "text": "Уточните, пожалуйста",
      "author": {
        "userId": "user-2",
        "role": "APPROVER"
      },
      "stageId": "8a7b6c5d-...",
      "stageName": "Проверка",
      "stageIterationIdx": 1,
      "participantId": "22222222-...",
      "createdAt": "2026-09-16T14:30:00Z",
      "parentId": null,
      "attachments": []
    }
  ],
  "meta": {
    "page": 0,
    "size": 50,
    "totalElements": 12,
    "totalPages": 1,
    "summary": {
      "remarks": {
        "total": 8,
        "active": 2,
        "inProgress": 0,
        "resolved": 4,
        "rejected": 1,
        "notRequired": 1,
        "autoProcessed": 0,
        "closed": 0
      },
      "comments": {
        "total": 4
      }
    }
  }
}
```

### 25.5 GET /entities/approval-history — История событий

**Параметры:**
- `entityType` (обязательный)
- `entitySubtype`
- `entityId` (обязательный)
- `includeSystemEvents` — по умолчанию false
- `eventTypes` — фильтр
- `page`, `size`

**Ответ 200:**

```json
{
  "data": [
    {
      "eventId": "550e8400-...",
      "eventType": "approval.process.started",
      "occurredAt": "2026-09-15T10:30:00Z",
      "actorId": "user-1",
      "actorType": "User",
      "details": {
        "processId": "7c9e6679-..."
      }
    }
  ],
  "meta": { ... }
}
```

### 25.6 Секция `actions` — единый источник истины

**Правило.** Front не вычисляет, какие кнопки показывать. Это ответственность бэкенда.

**Почему:**
- Логика прав сложная.
- Дублирование на Front → расхождения.
- Изменение правил → менять и Front, и бэк.

**Флаги:**

| Флаг | Описание |
|---|---|
| `canStart` | Может запустить процесс |
| `canRecall` | Может отозвать |
| `canResume` | Может возобновить |
| `canEditRoute` | Может редактировать маршрут |
| `canDecide` | Может принять решение |
| `canAddAdditionalApprover` | Может добавить доп. согласующего |
| `canCreateRemark` | Может создать замечание |
| `canTakeRemarkIntoWork` | Может взять замечание в работу |
| `canResolveRemark` | Может исправить замечание |
| `canRejectRemark` | Может отклонить замечание |
| `canChooseReturnTarget` | Может выбрать целевой этап |
| `availableReturnTargets` | Список доступных целей возврата |
| `canCreateRevision` | Может создать ревизию |
| `canFinalDecision` | Может принять финальное решение |

### 25.7 Принципы реализации

**Оптимизация запросов:**
- JOIN-запросы.
- `@EntityGraph`.
- Batch-загрузка.

**Кэширование:**
- `ETag` по `process.version` + `updatedAt`.
- `Cache-Control: private, max-age=30`.
- Invalidation по событиям.

**Права в агрегатах:**
- Фильтрация по правам пользователя.
- `role` определяет, что показывать.
- `actions` определяет, что можно делать.

---

## 26. Группа 17: Настройки уведомлений

### 26.1 Назначение

**Задача (ADR-027).** business_context (§17.2) требует, чтобы периодичность напоминаний инициатору о процессах в статусе «На доработке» настраивалась администратором — без изменения этого через релиз. Эта группа из двух эндпоинтов закрывает открытый пробел, зафиксированный в `claude/gap-analysis-business-context-vs-architecture.md` («Напоминания»): ранее периодичность `ReminderJob` (`10_architecture.md` §11) была хардкодом «раз в день», без какого-либо API для её изменения.

**Минимальный дизайн.** Одна глобальная настройка (таблица `notification_settings`, `08_db_schema.md` §22) — без гранулярности per-шаблон или per-тип процесса, по аналогии с `ArchiveSettings`.

### 26.2 GET /admin/notification-settings — Получить настройки

**Доступ:** администратор.

**Ответ 200:**

```json
{
  "data": {
    "reminderEnabled": true,
    "reminderIntervalHours": 24,
    "updatedAt": "2026-09-18T10:00:00Z",
    "updatedBy": "admin-user"
  }
}
```

### 26.3 PUT /admin/notification-settings — Изменить настройки

**Доступ:** администратор.

**Тело запроса:**

```json
{
  "reminderEnabled": true,
  "reminderIntervalHours": 12
}
```

**Валидации:**
- `reminderIntervalHours > 0` (иначе `REMINDER_INTERVAL_INVALID`, см. §9.3).

**Ответ 200:**

```json
{
  "data": {
    "reminderEnabled": true,
    "reminderIntervalHours": 12,
    "updatedAt": "2026-09-18T16:00:00Z",
    "updatedBy": "admin-user"
  }
}
```

**Эффект.** `ReminderJob` подхватывает новое значение при следующем запуске (без релиза, без перезапуска приложения) — см. `10_architecture.md` §11.1, ADR-027.

---

## 27. Сводная таблица эндпоинтов

### 27.1 Шаблоны

| Метод | Путь |
|---|---|
| GET | `/templates` |
| GET | `/templates/{id}` |
| POST | `/templates` |
| PUT | `/templates/{id}` |
| POST | `/templates/{id}/publish` |
| POST | `/templates/{id}/fork` |
| POST | `/templates/{id}/deprecate` |
| POST | `/templates/{id}/archive` |
| POST | `/templates/match` |

### 27.2 Процессы

| Метод | Путь |
|---|---|
| POST | `/processes` |
| GET | `/processes` |
| GET | `/processes/{id}` |
| POST | `/processes/{id}/start` |
| POST | `/processes/{id}/submit` |
| POST | `/processes/{id}/approve-route` |
| POST | `/processes/{id}/reject-route` |
| POST | `/processes/{id}/recall` |
| POST | `/processes/{id}/resume` |

### 27.3 Этапы и итерации

| Метод | Путь |
|---|---|
| GET | `/processes/{id}/stages` |
| GET | `/processes/{id}/stages/{stageId}` |
| GET | `/processes/{id}/stages/{stageId}/iterations` |
| GET | `/processes/{id}/stages/{stageId}/iterations/{iterIdx}` |

### 27.4 Участники

| Метод | Путь |
|---|---|
| GET | `/processes/{id}/stages/{stageId}/participants` |
| POST | `/processes/{id}/stages/{stageId}/participants` |
| PATCH | `/processes/{id}/stages/{stageId}/participants/{pId}` |
| DELETE | `/processes/{id}/stages/{stageId}/participants/{pId}` |

### 27.5 Доп. согласующие

| Метод | Путь |
|---|---|
| POST | `/processes/{id}/stages/{stageId}/participants/{pId}/additional-approvers` |
| POST | `/processes/{id}/stages/{stageId}/additional-approvers/{aaId}/children` |
| GET | `/processes/{id}/stages/{stageId}/additional-approvers` |
| DELETE | `/processes/{id}/stages/{stageId}/additional-approvers/{aaId}` |
| POST | `/processes/{id}/stages/{stageId}/additional-approvers/{aaId}/decision` |

### 27.6 Решения

| Метод | Путь |
|---|---|
| POST | `/processes/{id}/stages/{stageId}/decisions` |
| GET | `/processes/{id}/stages/{stageId}/decisions` |

### 27.7 Замечания и комментарии

| Метод | Путь |
|---|---|
| POST | `/processes/{id}/remarks` |
| GET | `/processes/{id}/remarks` |
| GET | `/processes/{id}/remarks/{remarkId}` |
| POST | `/processes/{id}/remarks/{remarkId}/activate` |
| POST | `/processes/{id}/remarks/{remarkId}/mark-not-required` |
| POST | `/processes/{id}/remarks/{remarkId}/take-into-work` |
| POST | `/processes/{id}/remarks/{remarkId}/resolve` |
| POST | `/processes/{id}/remarks/{remarkId}/reject` |
| GET | `/processes/{id}/remarks/report` |
| POST | `/processes/{id}/comments` |
| GET | `/processes/{id}/comments` |

### 27.8 Возврат и ревизии

| Метод | Путь |
|---|---|
| GET | `/processes/{id}/stages/{stageId}/return-targets` |
| POST | `/processes/revision` |
| GET | `/entities/aggregate/{documentAggregateId}/revisions` |

### 27.9 Финальное решение

| Метод | Путь |
|---|---|
| POST | `/processes/{id}/final-decision` |
| GET | `/processes/{id}/final-decision` |

### 27.10 Архивация

| Метод | Путь |
|---|---|
| GET | `/processes/archived` |
| POST | `/processes/{id}/restore` |

### 27.11 Конфигурация

| Метод | Путь |
|---|---|
| GET | `/configs` |
| GET | `/configs/{id}` |
| POST | `/configs` |
| PUT | `/configs/{id}` |
| POST | `/configs/{id}/publish` |
| POST | `/configs/{id}/deprecate` |
| POST | `/configs/{id}/archive` |
| GET | `/configs/{id}/audit` |

### 27.12 Реестр guards и actions

| Метод | Путь |
|---|---|
| GET | `/registry/guards` |
| GET | `/registry/guards/{code}` |
| GET | `/registry/actions` |
| GET | `/registry/actions/{code}` |

### 27.13 Агрегированные эндпоинты для Front

| Метод | Путь |
|---|---|
| GET | `/entities/approval-view` |
| GET | `/entities/approval-actions` |
| GET | `/entities/approval-remarks` |
| GET | `/entities/approval-history` |

### 27.14 Настройки уведомлений

| Метод | Путь |
|---|---|
| GET | `/admin/notification-settings` |
| PUT | `/admin/notification-settings` |

---
