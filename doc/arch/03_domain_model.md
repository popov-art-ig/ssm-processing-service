# Доменная модель

**Версия:** 2.0 
**Назначение:** описание сущностей, их атрибутов, связей и инвариантов

---

## Оглавление

1. Назначение
2. Ключевые понятия
3. Агрегаты
4. Общая схема связей
5. RegistryAggregate
6. TemplateAggregate
7. ProcessAggregate
8. StateMachineAggregate
9. AuditAggregate
10. Инварианты

---

## 1. Назначение

Доменная модель описывает:
- какие сущности существуют;
- какие у них атрибуты;
- как они связаны;
- какие правила целостности действуют.

---

## 2. Ключевые понятия

### 2.1 Агрегат

**Агрегат** — группа связанных сущностей с единым корнем. Все изменения в агрегате проходят через корень.

### 2.2 Корень агрегата

**Корень** — главная сущность агрегата. Имеет собственный идентификатор. Управляет жизненным циклом агрегата.

### 2.3 Value Object

**Value Object** — неизменяемая сущность без собственного идентификатора. Определяется своими атрибутами.

### 2.4 Entity

**Entity** — сущность с собственным идентификатором. Может изменяться во времени.

---

## 3. Агрегаты

| Агрегат | Корень | Содержит | Назначение |
|---|---|---|---|
| **RegistryAggregate** | StatusRegistry | StatusRegistry, DecisionResultRegistry | Справочники изменяемых значений |
| **TemplateAggregate** | Template | StageTemplate, SlotTemplate, ApplicabilityRule | Шаблоны маршрутов |
| **ProcessAggregate** | ProcessInstance | StageInstance, StageIteration, Participant, AdditionalApprover, Decision, Remark, Comment, FinalDecision, ArchiveMetadata | Процессы согласования |
| **StateMachineAggregate** | StateMachineConfig | StateConfig, TransitionConfig | Конфигурация карт переходов |
| **AuditAggregate** | AuditEvent | ConfigAuditEvent | Аудит |

---

## 4. Общая схема связей

```
RegistryAggregate
└── StatusRegistry, DecisionResultRegistry (справочники)

TemplateAggregate
Template ──1:N── StageTemplate ──1:N── SlotTemplate
     │                             └──1:N── SlotTemplate (дети)
     └──1:N── ApplicabilityRule
     └──N:1── Template (parentTemplateId, форк/версионирование, см. ADR-023)

ProcessAggregate
ProcessInstance ──1:N── StageInstance ──1:N── StageIteration ──1:N── Participant
                                                                      ├──1:N── AdditionalApprover (иерархия)
                                                                      ├──1:N── Decision
                                                                      ├──1:N── Remark
                                                                      └──1:N── Comment

ProcessInstance ──0:1── FinalDecision
ProcessInstance ──1:1── ArchiveMetadata
ProcessInstance ──N:1── ProcessInstance (parentProcessId, агрегат документа)

StateMachineAggregate
StateMachineConfig ──1:N── StateConfig
                   ──1:N── TransitionConfig

AuditAggregate
AuditEvent, ConfigAuditEvent
```

---

## 5. RegistryAggregate

### 5.1 Назначение

Хранит справочники изменяемых значений. Позволяет расширять статусы и результаты решений без миграции БД.

### 5.2 StatusRegistry

**Назначение.** Справочник всех возможных статусов для сущностей.

| Поле | Тип | Описание |
|---|---|---|
| `code` | String | Код статуса (PK) |
| `entityType` | String | Тип сущности (PROCESS, STAGE, STAGE_ITERATION, PARTICIPANT, ADDITIONAL_APPROVER, FINAL_DECISION, REMARK) — полный список соответствует `StateMachineConfig.entityType` (см. §8.2) |
| `displayName` | String | Название для отображения |
| `description` | String? | Описание |
| `isTerminal` | Boolean | Финальный ли статус |
| `category` | String? | Категория для группировки |
| `metadata` | JSON | Доп. данные для UI |
| `isActive` | Boolean | Активен ли статус |
| `createdAt` | Timestamp | — |
| `updatedAt` | Timestamp | — |

**Правило:** `code` уникален в рамках `entityType`.

### 5.3 DecisionResultRegistry

**Назначение.** Справочник результатов решений.

| Поле | Тип | Описание |
|---|---|---|
| `code` | String | Код результата (PK) |
| `displayName` | String | Название |
| `description` | String? | Описание |
| `isPositive` | Boolean | Положительный результат |
| `isNegative` | Boolean | Отрицательный результат |
| `isTerminal` | Boolean | Терминальный результат |
| `metadata` | JSON | Доп. данные |
| `isActive` | Boolean | — |
| `createdAt` | Timestamp | — |

**Значения по умолчанию:**

| code | displayName | isPositive | isNegative |
|---|---|---|---|
| APPROVE | Согласовать | true | false |
| APPROVE_WITH_COMMENTS | Согласовать с замечаниями | true | false |
| REJECT | Отклонить | false | true |

---

## 6. TemplateAggregate

### 6.1 Назначение

Хранит шаблоны маршрутов. Каждый шаблон может иметь несколько версий.

### 6.2 Template

**Назначение.** Многоразовое типовое описание маршрута.

| Поле | Тип | Описание |
|---|---|---|
| `id` | UUID | Идентификатор |
| `name` | String | Наименование |
| `processType` | Enum | STANDARD / UNIFIED |
| `status` | Enum | Draft / Published / Deprecated / Archived |
| `version` | Int | Номер версии |
| `parentTemplateId` | UUID? | Ссылка на родительский при форке |
| `processIterationEnabled` | Boolean | Разрешает создание ревизий |
| `createdAt` | Timestamp | — |
| `createdBy` | UUID | — |
| `updatedAt` | Timestamp | — |
| `updatedBy` | UUID? | — |
| `publishedAt` | Timestamp? | — |
| `publishedBy` | UUID? | — |
| `responsibleRoles` | UUID[] | Роли ответственного (UNIFIED) |
| `requiresResponsibleApproval` | Boolean | Согласование маршрута ответственным (UNIFIED) |

**Убрано:** `maxProcessIterations`.

**Добавлено (ADR-023):** `responsibleRoles`, `requiresResponsibleApproval` — перенесены сюда из удалённой `TemplateVersion` (см. ниже).

**Семантика `processIterationEnabled`:**

| Значение | Что разрешено |
|---|---|
| `false` | Только правки в рамках одного документа (итерации этапов) |
| `true` | Ревизии документа (новые процессы в агрегате) |

**Версионирование (ADR-023).** Отдельной сущности `TemplateVersion` больше нет. `Template` версионируется по аналогии со `StateMachineConfig`: каждое изменение опубликованного шаблона создаёт новую строку `Template` со своим `id`, `version = предыдущая + 1` и `parentTemplateId`, указывающим на предыдущую версию (та же цепочка, что уже использовалась для форка). `stages` (`StageTemplate[]`), `applicabilityRules` (`ApplicabilityRule[]`) и `responsibleRoles`/`requiresResponsibleApproval` принадлежат непосредственно новой строке `Template`, а не отдельной версии. `ProcessInstance.templateRef` указывает на конкретную версию (конкретный `id` `Template`) — этого достаточно для Snapshot-on-Start, отдельное поле `templateVersion` в процессе не нужно (см. §7.2).

### 6.4 StageTemplate

**Назначение.** Описание одного этапа в шаблоне.

| Поле | Тип | Описание |
|---|---|---|
| `orderIdx` | Int | Порядок |
| `name` | String? | Опциональное название |
| `description` | String? | Опциональное описание |
| `stageType` | Enum | APPROVAL / SIGNING / ENDORSEMENT |
| `duration` | Int? | Срок в рабочих днях |
| `decisionMode` | Enum? | Только STANDARD |
| `executionOrder` | Enum? | Только STANDARD |
| `isMandatory` | Boolean | Обязательность этапа |
| `isOrderMandatory` | Boolean | Обязательность порядка |
| `allowedReturnStages` | Int[]? | Список orderIdx этапов для возврата |
| `actorSlots` | SlotTemplate[] | Слоты |

**Семантика `allowedReturnStages`:**

| Значение | Что значит |
|---|---|
| `null` или `[]` | Возврат только на текущий этап |
| `[1, 2]` | Можно вернуть на этап 1 или 2 |
| `[1, 2, 3]` | Можно вернуть на любой из этапов 1, 2, 3 |

**Правила:**
- Только прошлые и текущий этапы (≤ `orderIdx`).
- Без дублей.
- Работает всегда, независимо от `processIterationEnabled`.

### 6.5 SlotTemplate (унифицированный)

**Назначение.** Позиция участника в шаблоне.

| Поле | Тип | Описание |
|---|---|---|
| `id` | UUID | Идентификатор |
| `parentSlotId` | UUID? | Родитель (для доп. согласующих) |
| `stageTemplateId` | UUID? | Этап (для основных) |
| `slotType` | Enum | ACTOR / ADDITIONAL_APPROVER |
| `orderIdx` | Int | Порядок |
| `userId` | UUID? | Конкретный пользователь |
| `organizationId` | UUID? | Область поиска пользователя |
| `acceptableRoles` | UUID[] | Допустимые роли |
| `required` | Boolean | Обязательность |
| `isUserEditable` | Boolean | Можно заменить пользователя |
| `isOrganizationEditable` | Boolean | Можно заменить организацию |
| `isDeletable` | Boolean | Можно удалить слот |
| `dueOffset` | Enum? | H0 / H3 / H8 (только для доп.) |

**Check-констрейнт:**

```
(slotType = ACTOR AND parentSlotId IS NULL AND stageTemplateId IS NOT NULL)
OR
(slotType = ADDITIONAL_APPROVER AND parentSlotId IS NOT NULL AND stageTemplateId IS NULL)
```

**Check-констрейнт:**

```
isOrganizationEditable = false OR isUserEditable = true
```

### 6.6 ApplicabilityRule

**Назначение.** Правило применимости шаблона.

| Поле | Тип | Описание |
|---|---|---|
| `id` | UUID | — |
| `templateId` | UUID | Ссылка на шаблон (конкретную версию `Template`, см. §6.2) |
| `ruleIdx` | Int | Порядок правила |
| `entityTypes` | String[] | Типы сущностей |
| `entitySubtypes` | String[] | Подтипы |
| `attributeConditions` | JSON | Предикаты по атрибутам |

**Пример `attributeConditions`:**

```json
{
  "amount": { "gte": 1000000 },
  "priority": { "in": ["HIGH", "CRITICAL"] }
}
```

---

## 7. ProcessAggregate

### 7.1 Назначение

Хранит процессы, их состояние, решения и историю.

### 7.2 ProcessInstance

**Назначение.** Экземпляр процесса.

| Поле | Тип | Описание |
|---|---|---|
| `id` | UUID | Идентификатор |
| `entityRef` | EntityRef | Ссылка на сущность |
| `documentAggregateId` | UUID? | Идентификатор агрегата документа |
| `revisionLabel` | String? | Метка ревизии (Изм. 1, Доп. 2, ТР. 3) |
| `parentProcessId` | UUID? | Ссылка на предыдущий процесс агрегата |
| `templateRef` | UUID | Ссылка на конкретную версию `Template` (ADR-023 — версия зафиксирована самой ссылкой, отдельного номера версии не требуется) |
| `processType` | Enum | STANDARD / UNIFIED |
| `configVersion` | Int | Версия конфига state machine |
| `status` | Enum | Текущий статус |
| `initiatorId` | UUID | Инициатор |
| `responsibleUserId` | UUID? | Ответственный (UNIFIED) |
| `responsibleResolvedRoleRef` | UUID? | Роль ответственного |
| `createdAt` | Timestamp | — |
| `startedAt` | Timestamp? | — |
| `completedAt` | Timestamp? | — |
| `archivedAt` | Timestamp? | — |
| `autoArchiveScheduledAt` | Timestamp? | Плановая архивация |
| `version` | Int | Оптимистичная блокировка |

**Убрано:**
- `currentProcessIteration`
- `currentProcessIterationStartedAt`

**Семантика `revisionLabel`:**

| Пример | Что значит |
|---|---|
| `Изм. 1` | Первое изменение |
| `Доп. 2` | Второе дополнение |
| `Зам. 3` | Третья замена |
| `ТР. 4` | Четвёртое техническое решение |

**Свободная строка.** Модуль не проверяет формат.

**Семантика `documentAggregateId`:**

Все ревизии одного документа связаны через `documentAggregateId`.

**Уникальность:** `(documentAggregateId, revisionLabel)` — в рамках агрегата метки уникальны.

### 7.3 StageInstance

**Назначение.** Этап в маршруте.

| Поле | Тип | Описание |
|---|---|---|
| `id` | UUID | Идентификатор |
| `processId` | UUID | Ссылка на процесс |
| `orderIdx` | Int | Порядок |
| `originalOrderIdx` | Int | Из шаблона |
| `name` | String? | — |
| `description` | String? | — |
| `stageType` | Enum | — |
| `duration` | Int | — |
| `decisionMode` | Enum? | AND / ANY_APPROVE / ANY_REJECT / ANY_DECISION / FIRST_REJECT_FAIL_FAST — см. `01_glossary.md` §11 |
| `executionOrder` | Enum? | Parallel / Sequential — см. `01_glossary.md` §12 |
| `isMandatory` | Boolean | — |
| `isOrderMandatory` | Boolean | — |
| `allowedReturnStages` | Int[]? | Snapshot из шаблона |
| `status` | Enum | — |
| `startedAt` | Timestamp? | — |
| `dueAt` | Timestamp? | — |
| `completedAt` | Timestamp? | — |
| `iterations` | StageIteration[] | Итерации этапа |

**Добавлено:** `description`, `allowedReturnStages`.

**Реализация `decisionMode`/`executionOrder`.** Оба поля реализованы в guards/actions — см. `05_guards_actions_registry.md` §4.2 (модель агрегации) и §5.2 (`AssignParticipantTasks`/`AssignNextParticipantTask`), а также `04_state_machines.md` §5.2 и §7 (ADR-022).

### 7.4 StageIteration

**Назначение.** Круг выполнения этапа.

| Поле | Тип | Описание |
|---|---|---|
| `id` | UUID | — |
| `stageId` | UUID | Ссылка на этап |
| `iterationIdx` | Int | Номер итерации (1, 2, 3...) |
| `status` | Enum | Active / Completed / Superseded / Cancelled |
| `startedAt` | Timestamp | — |
| `completedAt` | Timestamp? | — |
| `participants` | Participant[] | Участники |

**Ключевое правило:** при активации этапа, если у него уже есть итерации, создаётся новая.

### 7.5 ProcessIteration (вычисляемое представление)

**Назначение.** Группировка этапов для отображения в UI.

**Не хранится физически.**

**Формула:**
```
processIteration = max(stageIteration.iterationIdx)
```

**Пример:**

```
Этап 1: Iter 1 ✅, Iter 2 ✅
Этап 2: Iter 1 ✅, Iter 2 (активный)
Этап 3: Iter 1, Iter 2 (ждёт)

processIteration = 2
```

### 7.6 Participant

**Назначение.** Конкретный сотрудник, назначенный на слот.

| Поле | Тип | Описание |
|---|---|---|
| `id` | UUID | — |
| `stageIterationId` | UUID | Ссылка на итерацию этапа |
| `userId` | UUID | — |
| `organizationId` | UUID? | — |
| `actorSlotRef` | UUID | Из какого слота |
| `resolvedRoleRef` | UUID | Роль, по которой резолвился |
| `orderIdx` | Int | Порядок работы участника на этапе (для `executionOrder = Sequential`; см. ADR-022) |
| `role` | Enum | APPROVER / SIGNER / ADDITIONAL_APPROVER / OBSERVER |
| `status` | Enum | Pending / Assigned / InProgress / Decided / AutoApproved / Revoked / Cancelled |
| `decision` | Enum? | — |
| `isUserEditable` | Boolean | — |
| `isOrganizationEditable` | Boolean | — |
| `isDeletable` | Boolean | — |
| `additionalApprovers` | AdditionalApprover[] | Доп. согласующие |

**Убрано:** `substitutionRef`.

**Семантика `orderIdx`.** Определяет очередь при `executionOrder = Sequential`: участники получают задачи по возрастанию `orderIdx`, следующий — только после того, как предыдущий принял решение (`Decided`) или автосогласован (`AutoApproved`). При `executionOrder = Parallel` поле не влияет на назначение задач (все участники стартуют в `Assigned` одновременно).

### 7.7 AdditionalApprover

**Назначение.** Дополнительный согласующий (с иерархией).

| Поле | Тип | Описание |
|---|---|---|
| `id` | UUID | — |
| `participantId` | UUID | Корневой участник |
| `parentAdditionalApproverId` | UUID? | Родитель (для иерархии) |
| `level` | Int | Уровень в иерархии (вычисляется) |
| `userId` | UUID | — |
| `organizationId` | UUID? | — |
| `assignedBy` | Enum | TEMPLATE / INITIATOR / PARTICIPANT / ADDITIONAL_APPROVER |
| `assignedByUserId` | UUID? | — |
| `dueAt` | Timestamp? | — |
| `dueOffset` | Enum | H0 / H3 / H8 |
| `status` | Enum | — |
| `recommendation` | Enum? | — |
| `isUserEditable` | Boolean | — |
| `isOrganizationEditable` | Boolean | — |
| `isDeletable` | Boolean | — |

**Добавлено:** `parentAdditionalApproverId`, `level`, значение `ADDITIONAL_APPROVER` в `assignedBy`.

**Убрано:** `substitutionRef`, лимит 10.

**Check-констрейнт:**

```
(assignedBy IN ('TEMPLATE', 'INITIATOR', 'PARTICIPANT') AND parentAdditionalApproverId IS NULL)
OR
(assignedBy = 'ADDITIONAL_APPROVER' AND parentAdditionalApproverId IS NOT NULL)
```

**Иерархия:**

```
Participant
├── AdditionalApprover 1 (assignedBy=PARTICIPANT, level=1)
│   ├── AdditionalApprover 1.1 (assignedBy=ADDITIONAL_APPROVER, level=2)
│   └── AdditionalApprover 1.2 (level=2)
└── AdditionalApprover 2 (assignedBy=INITIATOR, level=1)
```

### 7.8 Decision

**Назначение.** Решение основного участника (`Participant`), определяющее ход процесса. **Не унифицировано** с рекомендацией доп. согласующего (`AdditionalApprover.recommendation`) — см. `01_glossary.md` §10.1–10.2 и ADR-016.

| Поле | Тип | Описание |
|---|---|---|
| `id` | UUID | — |
| `participantId` | UUID | Участник, принявший решение |
| `result` | Enum | APPROVE / APPROVE_WITH_COMMENTS / REJECT |
| `comment` | String? | — |
| `auto` | Boolean | Автосогласование |
| `recordedAt` | Timestamp | — |

**Связь только с `Participant`.** `Decision.participantId` ссылается на `Participant` (`role IN [APPROVER, SIGNER]`), а не на `AdditionalApprover`. Рекомендация доп. согласующего хранится отдельно, в поле `AdditionalApprover.recommendation` (см. §7.7) — своей таблицы `decision` она не использует и не имеет к ней FK.

**Почему это важно.** `Decision` — единственный источник входных данных для `evaluateAggregation(stage)` (см. `05_guards_actions_registry.md` §4.2) и, тем самым, управляет переходами `StageStateMachine`. `AdditionalApprover.recommendation` носит исключительно информационный характер для основного согласующего и не участвует в вычислении агрегации и не влияет на переходы state machine.

### 7.9 Remark

**Назначение.** Замечание.

| Поле | Тип | Описание |
|---|---|---|
| `id` | UUID | — |
| `processId` | UUID | — |
| `stageId` | UUID | — |
| `stageIterationId` | UUID? | Ссылка на итерацию этапа (FK на `StageIteration`) |
| `participantId` | UUID? | Автор-участник (основной или доп. согласующий), если применимо |
| `authorId` | UUID | — |
| `authorRole` | Enum | APPROVER / ADDITIONAL_APPROVER |
| `text` | String | — |
| `status` | Enum | — |
| `activatedBy` | UUID? | — |
| `activatedAt` | Timestamp? | — |
| `resolvedBy` | UUID? | — |
| `resolvedAt` | Timestamp? | — |
| `rejectedBy` | UUID? | — |
| `rejectedAt` | Timestamp? | — |
| `rejectionReason` | String? | — |
| `attachments` | UUID[] | Вложения |
| `createdAt` | Timestamp | — |
| `updatedAt` | Timestamp | — |

**Убрано:** `processIterationIdx`.

**Добавлено:** `participantId` (см. `08_db_schema.md` §12.1, где эта колонка уже существовала в БД, но не была отражена в доменной модели).

**Изменено:** `stageIterationIdx: Int` → `stageIterationId: UUID?` — теперь корректная ссылка на `StageIteration`, а не «голое» число без внешнего ключа.

### 7.10 Comment

**Назначение.** Комментарий.

| Поле | Тип | Описание |
|---|---|---|
| `id` | UUID | — |
| `processId` | UUID | — |
| `stageId` | UUID? | — |
| `stageIterationId` | UUID? | Ссылка на итерацию этапа (FK на `StageIteration`) |
| `participantId` | UUID? | Автор-участник, если применимо |
| `authorId` | UUID | — |
| `authorRole` | Enum | — |
| `text` | String | — |
| `parentId` | UUID? | Вложенность |
| `attachments` | UUID[] | — |
| `createdAt` | Timestamp | — |

**Убрано:** `processIterationIdx`.

**Добавлено:** `participantId`.

**Изменено:** `stageIterationIdx: Int?` → `stageIterationId: UUID?`.

### 7.11 FinalDecision

**Назначение.** Финальное решение ответственного (UNIFIED).

| Поле | Тип | Описание |
|---|---|---|
| `id` | UUID | — |
| `processId` | UUID | — |
| `responsibleUserId` | UUID | — |
| `responsibleResolvedRoleRef` | UUID? | — |
| `result` | Enum | APPROVE / APPROVE_WITH_COMMENTS / REJECT |
| `comment` | String? | — |
| `recordedAt` | Timestamp | — |

**Убрано:** `substitutionRef`.

### 7.12 ArchiveMetadata

**Назначение.** Метаданные архивации.

| Поле | Тип | Описание |
|---|---|---|
| `processId` | UUID | PK |
| `archivedAt` | Timestamp | Момент архивации |
| `previousStatus` | Enum | Статус до архивации |
| `restoredAt` | Timestamp? | Если был восстановлен |
| `restoredBy` | UUID? | — |

---

## 8. StateMachineAggregate

### 8.1 Назначение

Хранит конфиги карт переходов.

### 8.2 StateMachineConfig

| Поле | Тип | Описание |
|---|---|---|
| `id` | UUID | — |
| `version` | Int | Версия |
| `entityType` | Enum | PROCESS / STAGE / STAGE_ITERATION / PARTICIPANT / ADDITIONAL_APPROVER / FINAL_DECISION / REMARK |
| `processType` | Enum? | STANDARD / UNIFIED |
| `status` | Enum | Draft / Published / Deprecated / Archived |
| `states` | StateConfig[] | Состояния |
| `transitions` | TransitionConfig[] | Переходы |
| `activeProcessCount` | Int | Счётчик активных процессов |
| `publishedAt` | Timestamp? | — |
| `publishedBy` | UUID? | — |

### 8.3 StateConfig

| Поле | Тип | Описание |
|---|---|---|
| `id` | UUID | — |
| `configId` | UUID | — |
| `code` | String | — |
| `displayName` | String | — |
| `isInitial` | Boolean | — |
| `isTerminal` | Boolean | — |
| `metadata` | JSON | — |

### 8.4 TransitionConfig

| Поле | Тип | Описание |
|---|---|---|
| `id` | UUID | — |
| `configId` | UUID | — |
| `code` | String | — |
| `fromState` | String | — |
| `toState` | String | — |
| `trigger` | Enum | USER_ACTION / SYSTEM_ACTION / TIMER |
| `guards` | String[] | Условия |
| `actions` | String[] | Действия |
| `emits` | String[] | События |
| `priority` | Int | Приоритет |
| `isActive` | Boolean | — |

---

## 9. AuditAggregate

### 9.1 AuditEvent

| Поле | Тип | Описание |
|---|---|---|
| `id` | UUID | — |
| `entityType` | String | — |
| `entityId` | UUID | — |
| `action` | String | — |
| `actorId` | UUID? | — |
| `actorType` | Enum | User / System / Timer |
| `payload` | JSON | — |
| `occurredAt` | Timestamp | — |

### 9.2 ConfigAuditEvent

| Поле | Тип | Описание |
|---|---|---|
| `id` | UUID | — |
| `configId` | UUID | — |
| `configVersion` | Int | — |
| `action` | Enum | CREATED / UPDATED / PUBLISHED / DEPRECATED / ARCHIVED |
| `actorId` | UUID | — |
| `diff` | JSON | — |
| `comment` | String? | — |
| `occurredAt` | Timestamp | — |

---

## 10. Инварианты

### 10.1 Общие инварианты

| Инвариант | Описание |
|---|---|
| Процесс привязан к одной сущности | `entityRef` неизменяем |
| `configVersion` неизменяем | После старта |
| `templateRef` неизменяем | После старта (версия шаблона зафиксирована самой ссылкой, ADR-023) |
| RouteSnapshot неизменяем | Кроме не взятых в работу этапов |
| Участник уникален | В рамках `stageIteration` |
| Слот уникален | В рамках `stageIteration` |
| FinalDecision immutable | Нет операций изменения |
| Опубликованный шаблон immutable | Только форк |
| Опубликованный конфиг immutable | Только новая версия |
| Этап не может быть закрыт с `Rejected` | Если идёт на итерацию этапа |
| `allowedReturnStages` ⊆ прошлых и текущего | — |
| `isOrganizationEditable=true` → `isUserEditable=true` | — |
| `revisionLabel` уникален в агрегате | — |
| `parentProcessId` — из того же агрегата | — |
| `Decision` и `AdditionalApprover.recommendation` — разные механизмы | `Decision` управляет ходом процесса; `recommendation` не влияет на state machine (см. §7.8, ADR-016) |

### 10.2 Инварианты итераций

| Инвариант | Описание |
|---|---|
| `stageIteration` создаётся при активации | Если у этапа уже есть итерации — новая |
| `processIteration` не хранится | Вычисляется |
| Лимит итераций отсутствует | — |

### 10.3 Инварианты доп. согласующих

| Инвариант | Описание |
|---|---|
| Иерархия без ограничений | Глубина и количество не ограничены |
| `level` вычисляется | По цепочке родителей |
| Все замечания поднимаются до основного | — |
| Клонирование: TEMPLATE/INITIATOR — да | Дети клонируются вместе с родителем |
| Клонирование: PARTICIPANT/ADDITIONAL_APPROVER — нет | — |

### 10.4 Инварианты видимости

| Инвариант | Описание |
|---|---|
| Доп. видит основных | Да |
| Доп. видит других доп. глобально | Да |
| Доп. видит своих «детей» | Да |
| Основной видит всех доп. | Да |

### 10.5 Инварианты ответственного (UNIFIED)

| Инвариант | Описание |
|---|---|
| Ответственный может быть участником | Да |
| Финальное решение не зависит от голосов | Да |
| Финальное решение immutable | Да |

---
