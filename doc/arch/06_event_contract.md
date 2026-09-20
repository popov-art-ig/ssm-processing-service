# Контракт событий

**Версия:** 2.1  
**Назначение:** полное описание всех доменных событий, публикуемых модулем «Согласование»

---

## Оглавление

1. Назначение и обоснование
2. Ключевые понятия
3. Принципы событийной модели
4. Соглашения и формат
5. Каталог событий: Процесс
6. Каталог событий: Этап
7. Каталог событий: Итерации этапа
8. Каталог событий: Задачи
9. Каталог событий: Решения
10. Каталог событий: Замечания и комментарии
11. Каталог событий: Доп. согласующие
12. Каталог событий: Возврат на этап
13. Каталог событий: Ревизии документа
14. Каталог событий: Финальное решение
15. Каталог событий: Архивация
16. Каталог событий: Сущность
17. Каталог событий: Конфигурация
18. Версионирование
19. Примеры payload
20. Сводная таблица

---

## 1. Назначение и обоснование

### 1.1 Что такое контракт событий

**Контракт событий** — формальное описание всех доменных событий, публикуемых модулем. Определяет:
- какие события существуют;
- когда они публикуются;
- какие данные передаются;
- как интерпретировать подписчикам.

### 1.2 Зачем нужен контракт

**Проблема.** Модуль не создаёт задачи и уведомления сам. Он публикует события, на которые подписываются:

| Внешняя система | Что делает по событию |
|---|---|
| Модуль задач | Создаёт задачи согласующим |
| Модуль уведомлений | Отправляет письма и уведомления в ЛК |
| Система аудита | Пишет журнал аудита |
| Аналитика | Собирает метрики и отчёты |
| Система-владелец сущности | Обновляет статус документа |

Без чёткого контракта:
- Подписчики не знают, что ожидать.
- Изменение структуры события ломает подписчиков.
- Нет гарантий доставки.
- Сложно тестировать.

**Решение.** Ввести формальный контракт:
- единый формат;
- версионирование;
- схема payload;
- гарантии доставки;
- полный каталог событий.

### 1.3 Что это даёт

| Что | Зачем |
|---|---|
| Единый формат | Все события выглядят одинаково |
| Каталог | Все знают, какие события есть |
| Версионирование | Безопасное изменение контракта |
| Гарантии доставки | Подписчики знают, что ожидать |
| Схемы payload | Валидация и типизация |
| AsyncAPI | Автогенерация клиентов и документации |

---

## 2. Ключевые понятия

### 2.1 Доменное событие (Domain Event)

**Доменное событие** — факт, который произошёл в домене. Описывает **что случилось**, а не **что делать**.

**Правильно:** `approval.process.started` — процесс запущен.  
**Неправильно:** `approval.notify.initiator` — уведомить инициатора.

### 2.2 Событие vs Команда

| Событие | Команда |
|---|---|
| `approval.process.started` | `StartProcess` |
| Констатация факта | Побуждение к действию |
| Past tense | Imperative |
| Один ко многим | Один к одному |
| Асинхронно | Синхронно |

### 2.3 Издатель (Publisher)

**Издатель** — модуль «Согласование». Публикует события при переходах состояний.

### 2.4 Подписчик (Subscriber)

**Подписчик** — внешняя система, подписанная на события.

### 2.5 Exchange / Queue

**Exchange** (обменник) — точка входа в RabbitMQ.  
**Queue** (очередь) — получатель сообщений для конкретного подписчика.

### 2.6 Payload (Полезная нагрузка)

**Payload** — данные события: идентификаторы, атрибуты, метаданные.

### 2.7 Event Envelope (Обёртка события)

**Event Envelope** — общая структура, в которую оборачивается любое событие. Содержит метаданные и payload.

### 2.8 Outbox (Исходящий ящик)

**Outbox** — паттерн надёжной публикации. Событие сохраняется в БД в той же транзакции, что и изменение состояния, затем публикуется в брокер.

### 2.9 Гарантия доставки

**At-least-once** (минимум один раз) — событие доставляется, но может дублироваться. Подписчики должны быть **идемпотентными**.

---

## 3. Принципы событийной модели

### 3.1 Асинхронность

События публикуются асинхронно. Модуль не ждёт реакции подписчиков.

### 3.2 Идемпотентность

Подписчики должны корректно обрабатывать повторные события.

**Как.** В каждом событии есть `eventId` — уникальный идентификатор.

### 3.3 Порядок

События одного типа для одной сущности доставляются в порядке публикации.

**Как.** Использование `routing_key = aggregateId` в RabbitMQ.

### 3.4 Надёжность

Публикация через паттерн **Outbox**.

**Как.** Событие сначала в БД (в транзакции), затем в RabbitMQ (отдельный процесс).

### 3.5 Версионирование

Каждое событие имеет версию.

**Как.** Поле `eventVersion` в envelope.

### 3.6 Расширяемость

Payload может расширяться новыми полями без ломающих изменений.

**Как.** Только additive-изменения в минорных версиях.

---

## 4. Соглашения и формат

### 4.1 Именование событий

**Формат:** `<module>.<entity>.<action>`

| Часть | Пример | Описание |
|---|---|---|
| Модуль | `approval` | Идентификатор модуля |
| Сущность | `process`, `stage`, `task` | К какой сущности относится |
| Действие | `started`, `completed`, `rejected` | Past tense |

**Примеры:**
- `approval.process.started`
- `approval.stage.completed`
- `approval.remark.activated`

### 4.2 Обёртка события (Event Envelope)

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "approval.process.started",
  "eventVersion": "1.0",
  "occurredAt": "2026-09-15T10:30:00Z",
  "aggregateType": "ProcessInstance",
  "aggregateId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "correlationId": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
  "causationId": "9b2f4e8a-5c3d-4e2f-8a1b-7d6c5e4f3a2b",
  "actorId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "actorType": "User",
  "configVersion": 3,
  "payload": { ... }
}
```

| Поле | Описание |
|---|---|
| `eventId` | Уникальный идентификатор (UUID) |
| `eventType` | Тип события |
| `eventVersion` | Версия контракта |
| `occurredAt` | Момент публикации (ISO 8601 UTC) |
| `aggregateType` | Тип агрегата |
| `aggregateId` | Идентификатор агрегата |
| `correlationId` | Идентификатор бизнес-операции |
| `causationId` | Идентификатор события-причины |
| `actorId` | Кто инициировал |
| `actorType` | User / System / Timer |
| `configVersion` | Версия конфига SSM |
| `payload` | Полезная нагрузка |

### 4.3 Exchange и очереди (RabbitMQ)

| Exchange | Routing Key | Очереди |
|---|---|---|
| `approval.process.events` | `process.{processId}` | task.queue, notification.queue, audit.queue, analytics.queue |
| `approval.task.events` | `task.{processId}` | task.queue, notification.queue |
| `approval.decision.events` | `decision.{processId}` | notification.queue, audit.queue, analytics.queue |
| `approval.remark.events` | `remark.{processId}` | notification.queue, audit.queue |
| `approval.revision.events` | `revision.{aggregateId}` | notification.queue, audit.queue |
| `approval.archive.events` | `archive.{processId}` | audit.queue, analytics.queue |
| `approval.entity.events` | `entity.{entityId}` | entity.queue |
| `approval.config.events` | `config.{configId}` | audit.queue, admin.queue |

**Правило:** `routing_key` содержит `aggregateId` для порядка событий по сущности.

### 4.4 Формат данных

- Все идентификаторы — UUID v4.
- Все даты/время — ISO 8601 UTC.
- Все строки — UTF-8.
- Enum-значения — UPPER_SNAKE_CASE.

### 4.5 Принцип: только идентификаторы

**Правило.** API и события отдают **идентификаторы**, а не отображаемые имена. Резолвинг — ответственность подписчика.

**Не отдаётся:** `displayName`, `organizationDisplayName`, `resolvedRoleDisplayName`, `statusDisplayName`.

**Отдаётся:** `userId`, `organizationId`, `role`, `status`, `decision`.

---

## 5. Каталог событий: Процесс

### 5.1 `approval.process.created`

**Когда публикуется.** Процесс создан (переход в Draft).

**Exchange:** `approval.process.events`.

**Кто подписывается:** Audit, Analytics.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "entityRef": {
    "entityType": "Document",
    "entitySubtype": "PD",
    "entityId": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
  },
  "documentAggregateId": "agg-11111111-...",
  "revisionLabel": "Изм. 1",
  "parentProcessId": null,
  "templateRef": "d3d94468-...",
  "processType": "STANDARD",
  "initiatorId": "user-1",
  "createdAt": "2026-09-15T10:30:00Z"
}
```

**Изменено (ADR-023):** поле `templateVersion` убрано — версия шаблона зафиксирована самой ссылкой `templateRef` (конкретная версионная строка `Template`, версионируемая через `parentTemplateId`-цепочку), отдельный номер версии не передаётся.

### 5.2 `approval.process.started`

**Когда публикуется.** Процесс запущен.

**Exchange:** `approval.process.events`.

**Кто подписывается:** Task, Notification, Audit, Analytics.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "processType": "STANDARD",
  "initiatorId": "user-1",
  "startedAt": "2026-09-15T10:30:00Z",
  "configVersion": 3,
  "stagesCount": 3,
  "firstStageId": "8a7b6c5d-..."
}
```

### 5.3 `approval.process.recalled`

**Когда публикуется.** Процесс отозван.

**Exchange:** `approval.process.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "initiatorId": "user-1",
  "previousStatus": "IN_PROGRESS",
  "recalledAt": "2026-09-15T11:00:00Z",
  "reason": "Обнаружены ошибки в документе"
}
```

### 5.4 `approval.process.resumed`

**Когда публикуется.** Процесс возобновлён после отзыва или доработки.

**Exchange:** `approval.process.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "initiatorId": "user-1",
  "resumedAt": "2026-09-15T12:00:00Z",
  "targetStageId": "11111111-...",
  "targetStageOrderIdx": 1,
  "newIterationIdx": 2,
  "stagesReset": [2, 3]
}
```

**Важно:** `targetStageId` — целевой этап возврата. `stagesReset` — этапы, сброшенные в Pending.

### 5.5 `approval.process.completed`

**Когда публикуется.** Процесс достиг финального статуса.

**Exchange:** `approval.process.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "result": "APPROVED",
  "completedAt": "2026-09-15T14:00:00Z",
  "durationDays": 3,
  "stagesSummary": [
    {
      "stageId": "8a7b6c5d-...",
      "status": "APPROVED",
      "iterationsCount": 1
    },
    {
      "stageId": "9b8c7d6e-...",
      "status": "APPROVED",
      "iterationsCount": 2
    }
  ]
}
```

**Значения `result`:** `APPROVED`, `APPROVED_WITH_COMMENTS`, `REJECTED`.

### 5.6 `approval.process.rework-required`

**Когда публикуется.** Процесс требует доработки (этап отклонён).

**Exchange:** `approval.process.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "stageId": "8a7b6c5d-...",
  "stageIterationIdx": 1,
  "reworkAt": "2026-09-15T13:00:00Z",
  "reason": "STAGE_REJECTED",
  "activeRemarksCount": 3,
  "availableReturnTargets": [1, 2],
  "initiatorId": "user-1"
}
```

**Новое:** `availableReturnTargets` — список `orderIdx` этапов, на которые можно вернуть.

---

## 6. Каталог событий: Этап

### 6.1 `approval.stage.activated`

**Когда публикуется.** Этап активирован.

**Exchange:** `approval.process.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "stageId": "8a7b6c5d-...",
  "stageOrderIdx": 1,
  "stageName": "Проверка",
  "stageType": "APPROVAL",
  "iterationIdx": 2,
  "startedAt": "2026-09-15T10:31:00Z",
  "dueAt": "2026-09-20T10:31:00Z",
  "durationDays": 5,
  "participants": [
    {
      "participantId": "11111111-...",
      "userId": "user-1",
      "organizationId": "org-1",
      "role": "APPROVER"
    },
    {
      "participantId": "22222222-...",
      "userId": "user-2",
      "organizationId": "org-1",
      "role": "APPROVER"
    }
  ]
}
```

### 6.2 `approval.stage.completed`

**Когда публикуется.** Этап успешно завершён.

**Exchange:** `approval.process.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "stageId": "8a7b6c5d-...",
  "stageOrderIdx": 1,
  "iterationIdx": 1,
  "result": "APPROVED",
  "completedAt": "2026-09-18T10:00:00Z",
  "durationDays": 3,
  "decisionsSummary": [
    {
      "participantId": "11111111-...",
      "userId": "user-1",
      "result": "APPROVE",
      "auto": false
    },
    {
      "participantId": "22222222-...",
      "userId": "user-2",
      "result": "APPROVE_WITH_COMMENTS",
      "auto": false
    }
  ]
}
```

### 6.3 `approval.stage.rejected`

**Когда публикуется.** Этап закрыт со статусом Rejected (при возврате на другой этап).

**Exchange:** `approval.process.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "stageId": "8a7b6c5d-...",
  "stageOrderIdx": 3,
  "iterationIdx": 1,
  "rejectedAt": "2026-09-18T11:00:00Z",
  "reason": "RETURN_TO_PRIOR_STAGE",
  "returnTargetStageId": "11111111-...",
  "rejectedBy": [
    {
      "participantId": "11111111-...",
      "userId": "user-1"
    }
  ]
}
```

**Примечание.** Публикуется в результате перехода `StageRejectedByReturn`, инициируемого действием `A-RT-002 RejectStagesFrom` на уровне процесса (см. `04_state_machines.md` §5.2, §11.4–11.5, `11_adr.md` ADR-025).

### 6.4 `approval.stage.auto-approved`

**Когда публикуется.** Этап автосогласован по истечении срока.

**Exchange:** `approval.process.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "stageId": "8a7b6c5d-...",
  "stageOrderIdx": 1,
  "iterationIdx": 1,
  "autoApprovedAt": "2026-09-20T10:31:00Z",
  "autoApprovedParticipants": [
    {
      "participantId": "11111111-...",
      "userId": "user-1"
    }
  ]
}
```

### 6.5 `approval.stage.cancelled`

**Когда публикуется.** Этап отменён при отзыве процесса.

**Exchange:** `approval.process.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "stageId": "8a7b6c5d-...",
  "cancelledAt": "2026-09-15T11:00:00Z",
  "reason": "PROCESS_RECALLED"
}
```

---

## 7. Каталог событий: Итерации этапа

### 7.1 `approval.stage-iteration.created`

**Когда публикуется.** Создана новая итерация этапа.

**Exchange:** `approval.process.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "stageId": "8a7b6c5d-...",
  "iterationIdx": 2,
  "createdAt": "2026-09-15T13:00:00Z",
  "reason": "REWORK",
  "clonedFromIterationIdx": 1
}
```

### 7.2 `approval.stage-iteration.superseded`

**Когда публикуется.** Итерация заменена новой.

**Exchange:** `approval.process.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "stageId": "8a7b6c5d-...",
  "iterationIdx": 1,
  "supersededAt": "2026-09-15T13:00:00Z",
  "supersededByIterationIdx": 2
}
```

### 7.3 `approval.stage-iteration.completed`

**Когда публикуется.** Итерация этапа завершена.

**Exchange:** `approval.process.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "stageId": "8a7b6c5d-...",
  "iterationIdx": 1,
  "completedAt": "2026-09-18T10:00:00Z",
  "result": "APPROVED"
}
```

---

## 8. Каталог событий: Задачи

### 8.1 `approval.task.assigned`

**Когда публикуется.** Задача назначена участнику.

**Exchange:** `approval.task.events`.

**Кто подписывается:** Task System, Notification System.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "stageId": "8a7b6c5d-...",
  "stageIterationIdx": 1,
  "participantId": "11111111-...",
  "userId": "user-1",
  "organizationId": "org-1",
  "role": "APPROVER",
  "orderIdx": 0,
  "dueAt": "2026-09-20T10:31:00Z",
  "assignedAt": "2026-09-15T10:31:00Z"
}
```

**Значения `role`:** `APPROVER`, `SIGNER`, `ADDITIONAL_APPROVER` (неиспользуемое значение — см. `11_adr.md` ADR-016), `OBSERVER`.

**Примечание (ADR-022).** `orderIdx` заполнен для всех участников этапа; для `executionOrder = Sequential` определяет порядок назначения — событие публикуется по мере назначения (сразу всем при `Parallel`, по очереди при `Sequential`, см. `04_state_machines.md` §7.2).

### 8.2 `approval.task.revoked`

**Когда публикуется.** Задача отозвана.

**Exchange:** `approval.task.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "stageId": "8a7b6c5d-...",
  "stageIterationIdx": 1,
  "participantId": "11111111-...",
  "userId": "user-1",
  "revokedAt": "2026-09-18T10:00:00Z",
  "reason": "STAGE_CLOSED"
}
```

**Значения `reason`:** `STAGE_CLOSED`, `PROCESS_RECALLED`, `ITERATION_SUPERSEDED`, `AUTO_APPROVED`.

### 8.3 `approval.task.expired`

**Когда публикуется.** Срок задачи истёк.

**Exchange:** `approval.task.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "stageId": "8a7b6c5d-...",
  "stageIterationIdx": 1,
  "participantId": "11111111-...",
  "userId": "user-1",
  "expiredAt": "2026-09-20T10:31:00Z"
}
```

---

## 9. Каталог событий: Решения

### 9.1 `approval.decision.recorded`

**Когда публикуется.** Основной участник (`Participant`) принял решение.

**Exchange:** `approval.decision.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "stageId": "8a7b6c5d-...",
  "stageIterationIdx": 1,
  "participantId": "11111111-...",
  "userId": "user-1",
  "role": "APPROVER",
  "result": "APPROVE_WITH_COMMENTS",
  "comment": "Нужно уточнить раздел 3",
  "auto": false,
  "recordedAt": "2026-09-16T14:00:00Z"
}
```

**Значения `result`:** `APPROVE`, `APPROVE_WITH_COMMENTS`, `REJECT`.

**Значения `role`:** `APPROVER`, `SIGNER`.

**Не унифицировано с рекомендациями доп. согласующих (ADR-016).** Это событие публикуется только для решений `Participant` (основного согласующего/подписанта) — оно управляет ходом процесса через `evaluateAggregation(stage)` (`05_guards_actions_registry.md` §4.2). Рекомендации доп. согласующих — отдельное, информационное событие `approval.additional-approver.recommended` (см. §11.2), не влияющее на состояние этапа/процесса. Формулировка «унифицировано», ранее присутствовавшая в этом разделе, была неверной — см. `11_adr.md` ADR-016.

---

## 10. Каталог событий: Замечания и комментарии

### 10.1 `approval.remark.created`

**Когда публикуется.** Создано замечание.

**Exchange:** `approval.remark.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "stageId": "8a7b6c5d-...",
  "stageIterationIdx": 1,
  "remarkId": "44444444-...",
  "authorId": "user-1",
  "authorParticipantId": "11111111-...",
  "authorRole": "APPROVER",
  "text": "Требуется уточнить сроки",
  "status": "ACTIVE",
  "attachments": [
    {
      "fileId": "file-1",
      "fileName": "comment.pdf"
    }
  ],
  "createdAt": "2026-09-16T14:00:00Z"
}
```

**Изменено:** добавлено `authorParticipantId` — ссылка на автора-участника (основного или доп. согласующего), соответствует полю `Remark.participantId` (см. `03_domain_model.md` §7.9).

### 10.2 `approval.remark.activated`

**Когда публикуется.** Основной согласующий активировал замечание доп.

**Exchange:** `approval.remark.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "remarkId": "44444444-...",
  "activatedBy": "user-1",
  "activatedAt": "2026-09-16T15:00:00Z"
}
```

### 10.3 `approval.remark.marked-not-required`

**Когда публикуется.** Замечание помечено «не требуется».

**Exchange:** `approval.remark.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "remarkId": "44444444-...",
  "markedBy": "user-1",
  "markedAt": "2026-09-16T15:00:00Z"
}
```

### 10.4 `approval.remark.auto-processed`

**Когда публикуется.** Замечание авто-обработано при закрытии этапа.

**Exchange:** `approval.remark.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "remarkId": "44444444-...",
  "autoProcessedAt": "2026-09-18T10:00:00Z",
  "reason": "STAGE_CLOSED"
}
```

### 10.5 `approval.remark.resolved`

**Когда публикуется.** Замечание исправлено инициатором.

**Exchange:** `approval.remark.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "remarkId": "44444444-...",
  "resolvedBy": "user-initiator",
  "resolvedAt": "2026-09-16T16:00:00Z",
  "comment": "Исправлено"
}
```

### 10.6 `approval.remark.rejected`

**Когда публикуется.** Замечание отклонено инициатором.

**Exchange:** `approval.remark.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "remarkId": "44444444-...",
  "rejectedBy": "user-initiator",
  "rejectedAt": "2026-09-16T16:00:00Z",
  "reason": "Не относится к документу"
}
```

### 10.7 `approval.remark.closed`

**Когда публикуется.** Замечание закрыто при согласовании.

**Exchange:** `approval.remark.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "remarkId": "44444444-...",
  "closedAt": "2026-09-18T10:00:00Z",
  "reason": "APPROVED"
}
```

### 10.8 `approval.comment.created`

**Когда публикуется.** Создан комментарий.

**Exchange:** `approval.remark.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "stageId": "8a7b6c5d-...",
  "stageIterationIdx": 1,
  "commentId": "55555555-...",
  "authorId": "user-1",
  "authorParticipantId": "11111111-...",
  "authorRole": "APPROVER",
  "text": "Уточните, пожалуйста",
  "parentId": null,
  "createdAt": "2026-09-16T14:30:00Z"
}
```

**Изменено:** добавлено `authorParticipantId` — симметрично `approval.remark.created` (см. §10.1, `03_domain_model.md` §7.10).

---

## 11. Каталог событий: Доп. согласующие

### 11.1 `approval.additional-approver.assigned`

**Когда публикуется.** Доп. согласующий назначен.

**Exchange:** `approval.task.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "stageId": "8a7b6c5d-...",
  "stageIterationIdx": 1,
  "participantId": "11111111-...",
  "additionalApproverId": "33333333-...",
  "parentAdditionalApproverId": null,
  "userId": "user-10",
  "organizationId": "org-1",
  "assignedBy": "PARTICIPANT",
  "assignedByUserId": "user-1",
  "level": 1,
  "dueAt": "2026-09-20T07:31:00Z",
  "dueOffset": "H3",
  "assignedAt": "2026-09-15T10:32:00Z"
}
```

**Значения `assignedBy`:** `TEMPLATE`, `INITIATOR`, `PARTICIPANT`, `ADDITIONAL_APPROVER`.

### 11.2 `approval.additional-approver.recommended`

**Когда публикуется.** Доп. согласующий дал рекомендацию.

**Exchange:** `approval.decision.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "stageId": "8a7b6c5d-...",
  "stageIterationIdx": 1,
  "additionalApproverId": "33333333-...",
  "parentAdditionalApproverId": null,
  "userId": "user-10",
  "level": 1,
  "recommendation": "REJECT",
  "comment": "Не вижу оснований",
  "recommendedAt": "2026-09-16T15:00:00Z"
}
```

**Важно (ADR-016).** Это отдельное, информационное событие — не вариант `approval.decision.recorded` (§9.1). Рекомендация доп. согласующего видна основному участнику как справочная информация, но не участвует в `evaluateAggregation(stage)` и не влияет на переходы `StageStateMachine`/`ProcessStateMachine`.

### 11.3 `approval.additional-approver.revoked`

**Когда публикуется.** Доп. согласующий отозван.

**Exchange:** `approval.task.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "additionalApproverId": "33333333-...",
  "revokedAt": "2026-09-18T10:00:00Z",
  "reason": "MAIN_APPROVER_DECIDED"
}
```

### 11.4 `approval.additional-approver.removed`

**Когда публикуется.** Доп. согласующий удалён.

**Exchange:** `approval.task.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "additionalApproverId": "33333333-...",
  "removedBy": "user-1",
  "removedAt": "2026-09-16T10:00:00Z"
}
```

---

## 12. Каталог событий: Возврат на этап

### 12.1 `approval.process.return-targets-available`

**Когда публикуется.** Доступны цели возврата (при отклонении).

**Exchange:** `approval.process.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "currentStageId": "9b8c7d6e-...",
  "currentStageOrderIdx": 3,
  "allowedReturnTargets": [
    { "stageId": "11111111-...", "orderIdx": 1, "name": "Проверка" },
    { "stageId": "8a7b6c5d-...", "orderIdx": 2, "name": "Согласование" },
    { "stageId": "9b8c7d6e-...", "orderIdx": 3, "name": "Утверждение" }
  ],
  "requiresUserChoice": true,
  "initiatorId": "user-1"
}
```

---

## 13. Каталог событий: Ревизии документа

### 13.1 `approval.process.revision-created`

**Когда публикуется.** Создана новая ревизия документа.

**Exchange:** `approval.revision.events`.

**Кто подписывается:** Notification, Audit, Analytics, Entity System.

**Payload:**

```json
{
  "documentAggregateId": "agg-11111111-...",
  "newProcessId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "parentProcessId": "aaaaaaaa-...",
  "revisionLabel": "Изм. 2",
  "newEntityRef": {
    "entityType": "Document",
    "entitySubtype": "PD",
    "entityId": "new-doc-id"
  },
  "interruptPrevious": true,
  "previousProcessStatus": "RECALLED",
  "createdBy": "user-1",
  "createdAt": "2026-09-15T10:30:00Z"
}
```

### 13.2 `approval.process.revision-started`

**Когда публикуется.** Новая ревизия запущена в согласование.

**Exchange:** `approval.revision.events`.

**Payload:**

```json
{
  "documentAggregateId": "agg-11111111-...",
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "revisionLabel": "Изм. 2",
  "startedAt": "2026-09-15T11:00:00Z"
}
```

---

## 14. Каталог событий: Финальное решение (UNIFIED)

### 14.1 `approval.unified.route.submitted`

**Когда публикуется.** Инициатор отправил маршрут ответственному.

**Exchange:** `approval.process.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "initiatorId": "user-1",
  "responsibleUserId": "user-resp",
  "submittedAt": "2026-09-15T10:30:00Z"
}
```

### 14.2 `approval.unified.route.approved`

**Когда публикуется.** Ответственный согласовал маршрут.

**Exchange:** `approval.process.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "responsibleUserId": "user-resp",
  "approvedAt": "2026-09-15T11:00:00Z"
}
```

### 14.3 `approval.unified.route.rejected`

**Когда публикуется.** Ответственный отклонил маршрут.

**Exchange:** `approval.process.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "responsibleUserId": "user-resp",
  "rejectedAt": "2026-09-15T11:00:00Z",
  "comment": "Нужен другой маршрут"
}
```

### 14.4 `approval.unified.awaiting-final-decision`

**Когда публикуется.** Все этапы пройдены, ожидается финальное решение.

**Exchange:** `approval.process.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "responsibleUserId": "user-resp",
  "awaitingSince": "2026-09-18T10:00:00Z"
}
```

### 14.5 `approval.unified.final-decision.recorded`

**Когда публикуется.** Ответственный принял финальное решение.

**Exchange:** `approval.decision.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "responsibleUserId": "user-resp",
  "result": "APPROVED",
  "comment": "Согласовано",
  "recordedAt": "2026-09-18T12:00:00Z"
}
```

---

## 15. Каталог событий: Архивация

### 15.1 `approval.process.auto-archived`

**Когда публикуется.** Процесс автоматически архивирован.

**Exchange:** `approval.archive.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "archivedAt": "2027-03-15T10:00:00Z",
  "previousStatus": "APPROVED",
  "completedAt": "2026-09-15T14:00:00Z"
}
```

### 15.2 `approval.process.restored`

**Когда публикуется.** Процесс восстановлен администратором.

**Exchange:** `approval.archive.events`.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "restoredAt": "2027-04-15T10:00:00Z",
  "restoredBy": "admin-user",
  "previousStatus": "APPROVED",
  "newAutoArchiveScheduledAt": "2027-10-12T10:00:00Z"
}
```

---

## 16. Каталог событий: Сущность

### 16.1 `approval.entity.status.changed`

**Когда публикуется.** Рекомендация для системы-владельца изменить статус сущности.

**Exchange:** `approval.entity.events`.

**Кто подписывается:** Entity System.

**Payload:**

```json
{
  "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "entityRef": {
    "entityType": "Document",
    "entitySubtype": "PD",
    "entityId": "f47ac10b-..."
  },
  "suggestedStatus": "APPROVED",
  "processResult": "APPROVED",
  "changedAt": "2026-09-15T14:00:00Z"
}
```

**Важно.** Модуль не меняет статус сущности сам. Он **сообщает** о необходимости.

---

## 17. Каталог событий: Конфигурация

### 17.1 `approval.config.published`

**Когда публикуется.** Новая версия карты переходов опубликована.

**Exchange:** `approval.config.events`.

**Payload:**

```json
{
  "configId": "config-123",
  "configVersion": 3,
  "entityType": "ProcessInstance",
  "processType": "STANDARD",
  "publishedAt": "2026-09-14T10:00:00Z",
  "publishedBy": "admin-user"
}
```

### 17.2 `approval.config.deprecated`

**Когда публикуется.** Версия карты помечена устаревшей.

**Exchange:** `approval.config.events`.

**Payload:**

```json
{
  "configId": "config-123",
  "configVersion": 2,
  "deprecatedAt": "2026-09-14T10:00:00Z",
  "deprecatedBy": "admin-user",
  "activeProcessCount": 0
}
```

---

## 18. Версионирование

### 18.1 Принципы

- Каждое событие имеет `eventVersion`.
- Минорная версия (`1.0` → `1.1`) — additive-изменения.
- Мажорная версия (`1.0` → `2.0`) — breaking changes.

### 18.2 Правила совместимости

| Изменение | Тип | Версия |
|---|---|---|
| Добавлено новое поле | Additive | Minor |
| Добавлено опциональное поле | Additive | Minor |
| Удалено поле | Breaking | Major |
| Изменено имя поля | Breaking | Major |
| Изменён тип поля | Breaking | Major |
| Изменён смысл поля | Breaking | Major |

### 18.3 Политика поддержки

- Одновременно поддерживаются 2 последние мажорные версии.
- При выпуске мажорной версии — уведомление подписчикам за 3 месяца.
- Старые версии помечаются deprecated.

---

## 19. Примеры payload

### 19.1 Полный пример: запуск процесса

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "approval.process.started",
  "eventVersion": "1.0",
  "occurredAt": "2026-09-15T10:30:00Z",
  "aggregateType": "ProcessInstance",
  "aggregateId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "correlationId": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
  "causationId": null,
  "actorId": "user-1",
  "actorType": "User",
  "configVersion": 3,
  "payload": {
    "processId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "processType": "STANDARD",
    "initiatorId": "user-1",
    "startedAt": "2026-09-15T10:30:00Z",
    "configVersion": 3,
    "stagesCount": 3,
    "firstStageId": "8a7b6c5d-..."
  }
}
```

### 19.2 Полный пример: решение участника

```json
{
  "eventId": "660e8400-e29b-41d4-a716-446655440001",
  "eventType": "approval.decision.recorded",
  "eventVersion": "1.0",
  "occurredAt": "2026-09-16T14:00:00Z",
  "aggregateType": "Participant",
  "aggregateId": "11111111-...",
  "correlationId": "3f2504e0-...",
  "causationId": "550e8400-...",
  "actorId": "user-1",
  "actorType": "User",
  "configVersion": 3,
  "payload": {
    "processId": "7c9e6679-...",
    "stageId": "8a7b6c5d-...",
    "stageIterationIdx": 1,
    "participantId": "11111111-...",
    "userId": "user-1",
    "role": "APPROVER",
    "result": "APPROVE_WITH_COMMENTS",
    "comment": "Нужно уточнить раздел 3",
    "auto": false,
    "recordedAt": "2026-09-16T14:00:00Z"
  }
}
```

### 19.3 Полный пример: создание ревизии

```json
{
  "eventId": "770e8400-...",
  "eventType": "approval.process.revision-created",
  "eventVersion": "1.0",
  "occurredAt": "2026-09-15T10:30:00Z",
  "aggregateType": "DocumentAggregate",
  "aggregateId": "agg-11111111-...",
  "correlationId": "...",
  "causationId": null,
  "actorId": "user-1",
  "actorType": "User",
  "configVersion": 3,
  "payload": {
    "documentAggregateId": "agg-11111111-...",
    "newProcessId": "7c9e6679-...",
    "parentProcessId": "aaaaaaaa-...",
    "revisionLabel": "Изм. 2",
    "newEntityRef": { ... },
    "interruptPrevious": true,
    "previousProcessStatus": "RECALLED",
    "createdBy": "user-1",
    "createdAt": "2026-09-15T10:30:00Z"
  }
}
```

---

## 20. Сводная таблица

| № | Событие | Exchange | Подписчики |
|---|---|---|---|
| 1 | `approval.process.created` | process.events | Audit, Analytics |
| 2 | `approval.process.started` | process.events | Task, Notify, Audit, Analytics |
| 3 | `approval.process.recalled` | process.events | Task, Notify, Audit |
| 4 | `approval.process.resumed` | process.events | Task, Notify, Audit |
| 5 | `approval.process.completed` | process.events | Notify, Audit, Analytics, Entity |
| 6 | `approval.process.rework-required` | process.events | Notify, Analytics |
| 7 | `approval.stage.activated` | process.events | Task, Notify, Analytics |
| 8 | `approval.stage.completed` | process.events | Notify, Audit, Analytics |
| 9 | `approval.stage.rejected` | process.events | Notify, Audit, Analytics |
| 10 | `approval.stage.auto-approved` | process.events | Notify, Audit |
| 11 | `approval.stage.cancelled` | process.events | Task, Notify |
| 12 | `approval.stage-iteration.created` | process.events | Audit |
| 13 | `approval.stage-iteration.superseded` | process.events | Audit |
| 14 | `approval.stage-iteration.completed` | process.events | Audit |
| 15 | `approval.task.assigned` | task.events | Task, Notify |
| 16 | `approval.task.revoked` | task.events | Task, Notify |
| 17 | `approval.task.expired` | task.events | Task, Notify, Audit |
| 18 | `approval.decision.recorded` | decision.events | Notify, Audit, Analytics |
| 19 | `approval.remark.created` | remark.events | Notify, Audit |
| 20 | `approval.remark.activated` | remark.events | Notify, Audit |
| 21 | `approval.remark.marked-not-required` | remark.events | Notify, Audit |
| 22 | `approval.remark.auto-processed` | remark.events | Audit |
| 23 | `approval.remark.resolved` | remark.events | Notify, Audit |
| 24 | `approval.remark.rejected` | remark.events | Notify, Audit |
| 25 | `approval.remark.closed` | remark.events | Audit |
| 26 | `approval.comment.created` | remark.events | Notify |
| 27 | `approval.additional-approver.assigned` | task.events | Task, Notify |
| 28 | `approval.additional-approver.recommended` | decision.events | Notify, Audit |
| 29 | `approval.additional-approver.revoked` | task.events | Task |
| 30 | `approval.additional-approver.removed` | task.events | Task, Notify |
| 31 | `approval.process.return-targets-available` | process.events | Notify |
| 32 | `approval.process.revision-created` | revision.events | Notify, Audit, Analytics, Entity |
| 33 | `approval.process.revision-started` | revision.events | Task, Notify |
| 34 | `approval.unified.route.submitted` | process.events | Notify, Audit |
| 35 | `approval.unified.route.approved` | process.events | Notify, Audit |
| 36 | `approval.unified.route.rejected` | process.events | Notify, Audit |
| 37 | `approval.unified.awaiting-final-decision` | process.events | Notify |
| 38 | `approval.unified.final-decision.recorded` | decision.events | Notify, Audit, Entity |
| 39 | `approval.process.auto-archived` | archive.events | Audit, Analytics |
| 40 | `approval.process.restored` | archive.events | Audit, Notify |
| 41 | `approval.entity.status.changed` | entity.events | Entity System |
| 42 | `approval.config.published` | config.events | Audit, Admin |
| 43 | `approval.config.deprecated` | config.events | Audit, Admin |

**Убрано (ADR-024):** `approval.task.reassigned` (замещения) — функциональность замещений участников убрана из модуля целиком.

---
