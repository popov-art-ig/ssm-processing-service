# Реестр условий и действий

**Версия:** 2.0  
**Назначение:** каталог всех guards (условий) и actions (действий), на которые ссылаются карты переходов

---

## Оглавление

1. Назначение и обоснование
2. Ключевые понятия
3. Структура реестра
4. Условия (Guards)
5. Действия (Actions)
6. Регистрация и область применения
7. Валидация
8. Точки расширения
9. Примеры использования
10. Сводная таблица

---

## 1. Назначение и обоснование

### 1.1 Что это

**Реестр условий и действий** — каталог всех guards и actions, используемых в картах переходов. Каждое условие и действие — отдельный именованный блок логики, переиспользуемый в разных переходах.

### 1.2 Зачем нужен

**Проблема.** Карты переходов — это данные. Но условия и действия — это код. Без реестра:

| Проблема | Последствие |
|---|---|
| Условия разбросаны | Дублирование, разные реализации одного и того же |
| Нет единого языка | Каждый называет по-своему |
| Сложно переиспользовать | Одно и то же пишется заново |
| Нет документации | Непонятно, что делает каждое условие |
| Нет контроля | Нельзя проверить, что карта ссылается на существующие условия |

**Решение.** Ввести реестр — единый каталог, где каждое условие и действие имеет:
- уникальный код;
- описание;
- параметры;
- зарегистрированный обработчик.

### 1.3 Что это даёт

| Что | Зачем |
|---|---|
| Единый язык | Все условия и действия называются одинаково |
| Переиспользование | Одно условие используется в разных переходах |
| Документация | Каждое условие и действие описано |
| Контроль | При публикации карты проверяется, что ссылки валидны |
| Расширяемость | Можно добавлять свои условия и действия |

---

## 2. Ключевые понятия

### 2.1 Guard (Условие)

**Предикат**, который должен быть истинным, чтобы переход выполнился.

**Особенности:**
- Возвращает `true` или `false`.
- Не имеет побочных эффектов.
- Может принимать параметры.

### 2.2 Action (Действие)

**Операция**, которая выполняется при переходе.

**Особенности:**
- Может изменять состояние.
- Может вызывать внешние системы.
- Может публиковать события.
- Не возвращает значение.

### 2.3 Trigger (Триггер)

**Что вызвало попытку перехода.**

| Триггер | Описание |
|---|---|
| **userAction** | Пользователь нажал кнопку |
| **systemAction** | Система автоматически инициировала |
| **timer** | Сработал таймер или расписание |

### 2.4 Context (Контекст)

**Данные, доступные условиям и действиям** во время выполнения перехода.

**Содержит:**
- сущность (процесс, этап, участник);
- текущего пользователя;
- параметры перехода;
- доступ к внешним сервисам (адаптеры).

### 2.5 Registry (Реестр)

**Каталог всех зарегистрированных условий и действий.**

---

## 3. Структура реестра

### 3.1 Запись реестра

```
GuardRegistryEntry
├── code: string
├── displayName: string
├── description: string
├── handler: string
├── paramsSchema: JSON
├── scope: GLOBAL | CUSTOM
├── categories: string[]
└── applicableEntities: string[]
```

### 3.2 Область применения (Scope)

| Scope | Описание |
|---|---|
| **GLOBAL** | Встроенное условие или действие |
| **CUSTOM** | Пользовательское, требует явной регистрации |

### 3.3 Категории

| Категория | Описание |
|---|---|
| **AUTHORIZATION** | Проверка прав |
| **VALIDATION** | Проверка данных |
| **LIFECYCLE** | Управление состояниями |
| **NOTIFICATION** | Публикация событий |
| **TASK** | Управление задачами |
| **DATA** | Работа с данными |
| **INTEGRATION** | Обращение к внешним системам |
| **TIMER** | Таймеры |
| **ITERATION** | Управление итерациями |
| **REVISION** | Управление ревизиями |
| **HIERARCHY** | Управление иерархией доп. согласующих |

---

## 4. Условия (Guards)

### 4.1 Условия процесса

#### G-P-001. IsInitiator — Является инициатором

**Что проверяет.** Текущий пользователь — инициатор процесса.

**Логика.**
```
context.currentUser.id == process.initiatorId
```

#### G-P-002. IsResponsible — Является ответственным

**Что проверяет.** Текущий пользователь — ответственный (UNIFIED).

**Логика.**
```
context.currentUser.id == process.responsibleUserId
```

#### G-P-003. IsAdmin — Является администратором

**Что проверяет.** Текущий пользователь — администратор.

**Логика.**
```
context.currentUser.roles.contains("Admin")
```

#### G-P-004. AllMandatorySlotsFilled — Все обязательные слоты заполнены

**Что проверяет.** В маршруте заполнены все обязательные слоты.

**Логика.**
```
∀ stage ∈ route.stages:
    ∀ slot ∈ stage.actorSlots where slot.required == true:
        slot.userId != null
```

#### G-P-005. AllDurationsValid — Все сроки корректны

**Что проверяет.** У всех этапов срок > 0.

**Логика.**
```
∀ stage ∈ route.stages:
    stage.duration > 0
```

#### G-P-006. AllActiveRemarksHandled — Все замечания обработаны

**Что проверяет.** Нет замечаний в статусах Active или InProgress.

**Логика.**
```
∀ remark ∈ process.remarks:
    remark.status NOT IN [Active, InProgress]
```

#### G-P-007. AllStagesApproved — Все этапы согласованы

**Что проверяет.** Все этапы процесса имеют статус Approved.

**Логика.**
```
∀ stage ∈ process.stages:
    stage.status == Approved
```

#### G-P-008. AllStagesApprovedWithComments — Все этапы согласованы с замечаниями

**Что проверяет.** Все этапы Approved или ApprovedWithComments, хотя бы один — с замечаниями.

**Логика.**
```
∀ stage ∈ process.stages:
    stage.status IN [Approved, ApprovedWithComments]
AND
∃ stage ∈ process.stages:
    stage.status == ApprovedWithComments
```

#### G-P-009. AnyStageRejected — Есть отклонение на этапе

**Что проверяет.** Хотя бы один этап отклонён или отправлен на доработку.

**Логика.**
```
∃ stage ∈ process.stages:
    stage.status IN [OnRework, Rejected]
```

#### G-P-010. AllStagesCompleted — Все этапы завершены

**Что проверяет.** Все этапы имеют статус Completed (UNIFIED).

**Логика.**
```
∀ stage ∈ process.stages:
    stage.status == Completed
```

#### G-P-011. ProcessIterationEnabled — Итерационность процесса включена

**Что проверяет.** Признак разрешения ревизий включён в шаблоне.

**Зачем.** Используется для создания ревизий документа.

**Логика.**
```
process.templateVersion.processIterationEnabled == true
```

---

### 4.2 Условия этапа

#### G-S-001. PreviousStageCompleted — Предыдущий этап завершён

**Что проверяет.** Предыдущий этап имеет финальный статус.

**Логика.**
```
previousStage.status IN [Approved, ApprovedWithComments, Completed]
```

#### Модель агрегации решений (общая для G-S-002 – G-S-004)

Guards G-S-002 – G-S-004 — это три тонких предиката поверх одной общей функции `evaluateAggregation(stage)`, реализующей все 5 значений `stage.decisionMode` (см. `01_glossary.md` §11 и `03_domain_model.md` §7.3). До ADR-022 эти guards жёстко реализовывали только режим `AND` (`AllApproved`/`AllApprovedWithComments`/`AnyRejected`), игнорируя `decisionMode`. Начиная с ADR-022 — параметризованы.

**Логика `evaluateAggregation(stage)`:**

```
decided = stage.participants where decision != null
pending = stage.participants where decision == null

switch stage.decisionMode:

  AND:                          // ждём всех, отказ не прерывает ожидание
    if pending.isEmpty():
        if ∃ decided.decision == Reject:              return REWORK
        elif ∃ decided.decision == ApproveWithComments: return APPROVED_WITH_COMMENTS
        else:                                            return APPROVED
    else:
        return WAIT

  FIRST_REJECT_FAIL_FAST:       // ждём всех, но первый отказ прерывает
    if ∃ decided.decision == Reject:
        return REWORK
    elif pending.isEmpty():
        if ∃ decided.decision == ApproveWithComments: return APPROVED_WITH_COMMENTS
        else:                                           return APPROVED
    else:
        return WAIT

  ANY_APPROVE:                  // первый согласовавший закрывает этап
    firstApprove = decided.filter(decision IN [Approve, ApproveWithComments])
                          .minBy(decidedAt)
    if firstApprove != null:
        return firstApprove.decision == ApproveWithComments
            ? APPROVED_WITH_COMMENTS : APPROVED
    else:
        return WAIT

  ANY_REJECT:                   // первый отклонивший отправляет на доработку
    if ∃ decided.decision == Reject:
        return REWORK
    else:
        return WAIT

  ANY_DECISION:                 // первое любое решение закрывает этап
    first = decided.minBy(decidedAt)
    if first != null:
        if first.decision == Reject:                  return REWORK
        elif first.decision == ApproveWithComments:    return APPROVED_WITH_COMMENTS
        else:                                           return APPROVED
    else:
        return WAIT
```

**Результат и переходы:**

| Результат | Переход StageStateMachine |
|---|---|
| `WAIT` | Нет перехода, этап остаётся `Active` |
| `APPROVED` | `StageApproved` (→ Approved) |
| `APPROVED_WITH_COMMENTS` | `StageApprovedWithComments` (→ ApprovedWithComments) |
| `REWORK` | `StageOnRework` (→ OnRework, см. ключевое правило `04_state_machines.md` §5.4) |

**Важно.** `evaluateAggregation` не переопределяет правило «этап не может быть закрыт с итогом Отклонён, если он идёт на новую итерацию» — результат `REWORK` всегда ведёт в `OnRework`, а не в терминальный `Rejected` (тот доступен только через `ReturnToPriorStage`, см. G-S-007).

#### G-S-002. AggregationApproved — Агрегация: согласовано

**Что проверяет.** `evaluateAggregation(stage) == APPROVED`.

**Параметры.** Нет — режим агрегации берётся из `stage.decisionMode`.

#### G-S-003. AggregationApprovedWithComments — Агрегация: согласовано с замечаниями

**Что проверяет.** `evaluateAggregation(stage) == APPROVED_WITH_COMMENTS`.

**Параметры.** Нет.

#### G-S-004. AggregationRework — Агрегация: на доработку

**Что проверяет.** `evaluateAggregation(stage) == REWORK`.

**Параметры.** Нет.

#### G-S-005. DueDateExpired — Истёк срок

**Что проверяет.** Текущее время превышает плановую дату завершения.

**Логика.**
```
context.now > stage.dueAt
```

#### G-S-006. ProcessRecalled — Процесс отозван

**Что проверяет.** Процесс в статусе Recalled.

**Логика.**
```
process.status == Recalled
```

#### G-S-007. ReturnToPriorStage — Возврат на предыдущий этап

**Что проверяет.** Текущий этап закрывается в связи с возвратом на более ранний этап.

**Логика.**
```
returnTargetStage.orderIdx < currentStage.orderIdx
```

---

### 4.3 Условия участника

#### G-PA-001. IsParticipant — Является участником

**Что проверяет.** Текущий пользователь — назначенный участник.

**Логика.**
```
context.currentUser.id == participant.userId
```

#### G-PA-002. HasValidDecision — Есть корректное решение

**Что проверяет.** Решение участника корректно.

**Логика.**
```
participant.decision IN [Approve, ApproveWithComments, Reject]
AND
(participant.decision != ApproveWithComments OR comment IS NOT NULL)
AND
(participant.decision != Reject OR (comment IS NOT NULL OR remark IS NOT NULL))
```

#### G-PA-003. StageClosed — Этап закрыт

**Что проверяет.** Этап имеет финальный статус.

**Логика.**
```
stage.status IN [Approved, ApprovedWithComments, Rejected, AutoApproved, Completed, Cancelled]
```

#### G-PA-004. IterationSuperseded — Итерация заменена

**Что проверяет.** Текущая итерация заменена новой.

**Логика.**
```
iteration.status == Superseded
```

**Убрано (ADR-024):** `G-PA-005 SubstitutionActive`. Ссылался на сущность `Substitution` (`periodFrom`/`periodTo`), никогда не описанную в схеме БД (`08_db_schema.md`) — функциональность замещений участников убрана из модуля целиком, см. `11_adr.md` ADR-024 и `00_vision_scope.md` §3.

#### G-PA-006. IsResponsibleAsParticipant — Ответственный как участник

**Что проверяет.** Ответственный является участником этапа и голосует.

**Зачем.** В UNIFIED ответственный может быть участником. Голос не влияет на финальное решение.

**Логика.**
```
participant.userId == process.responsibleUserId
```

#### G-PA-007. CanAssignTask — Можно назначить задачу (Sequential)

**Что проверяет.** Участник может перейти из `Pending` в `Assigned`: порядок работы на этапе — последовательный, и все участники этапа с меньшим `orderIdx` уже завершили работу.

**Логика.**
```
stage.executionOrder == Sequential
AND participant.status == Pending
AND ∀ p ∈ stage.participants where p.orderIdx < participant.orderIdx:
    p.status IN [Decided, AutoApproved]
```

**Зачем.** Определяет, чья очередь получить задачу при `executionOrder = Sequential`. Для `Parallel` переход `AssignTask` не используется — все участники стартуют в `Assigned` (см. `04_state_machines.md` §7.2).

---

### 4.4 Условия возврата на этап

#### G-RT-001. IsValidReturnTarget — Корректная цель возврата

**Что проверяет.** Выбранный этап входит в `allowedReturnStages` текущего этапа.

**Логика.**
```
targetStageId ∈ currentStage.allowedReturnStages
AND targetStage.orderIdx <= currentStage.orderIdx
```

#### G-RT-002. HasMultipleReturnTargets — Есть несколько целей возврата

**Что проверяет.** `allowedReturnStages` содержит > 1 этап.

**Зачем.** Определяет, показывать ли модалку выбора этапа.

**Логика.**
```
currentStage.allowedReturnStages.length > 1
```

---

### 4.5 Условия иерархии доп. согласующих

#### G-H-001. IsMainApprover — Является основным согласующим

**Что проверяет.** Текущий пользователь — основной согласующий этапа.

**Логика.**
```
context.currentUser.id IN stage.participants (role=APPROVER)
```

#### G-H-002. IsAdditionalApprover — Является доп. согласующим

**Что проверяет.** Текущий пользователь — доп. согласующий.

**Логика.**
```
context.currentUser.id IN additionalApprovers (любого уровня)
```

#### G-H-003. IsParentAdditionalApprover — Является родителем в иерархии

**Что проверяет.** Текущий пользователь — родитель доп. согласующего.

**Логика.**
```
additionalApprover.parentAdditionalApproverId.userId == context.currentUser.id
```

---

### 4.6 Условия замечания

#### G-R-001. ParticipantNotDecided — Участник ещё не принял решение

**Что проверяет.** Основной согласующий ещё не принял решение.

**Логика.**
```
mainApprover.decision == null
```

#### G-R-002. HasReason — Есть обоснование

**Что проверяет.** При отклонении замечания указана причина.

**Логика.**
```
remark.rejectionReason IS NOT NULL
AND LENGTH(remark.rejectionReason) > 0
```

#### G-R-003. ApproveOrApproveWithComments — Согласовано или согласовано с замечаниями

**Что проверяет.** Основной согласующий принял положительное решение.

**Логика.**
```
mainApprover.decision IN [Approve, ApproveWithComments]
```

---

### 4.7 Условия финального решения

#### G-FD-001. FinalDecisionPending — Финальное решение ожидает

**Что проверяет.** Процесс в статусе AwaitingFinalDecision.

**Логика.**
```
process.status == AwaitingFinalDecision
```

#### G-FD-002. CanAcceptFinalDecision — Может принять финальное решение

**Что проверяет.** Пользователь — ответственный и процесс в нужном статусе.

**Логика.**
```
context.currentUser.id == process.responsibleUserId
AND process.status == AwaitingFinalDecision
```

---

### 4.8 Условия архивации

#### G-A-001. AutoArchiveTimeReached — Время автоархивации наступило

**Что проверяет.** С момента завершения прошло ≥ 180 дней.

**Логика.**
```
context.now >= process.autoArchiveScheduledAt
AND process.status IN [Approved, ApprovedWithComments, Rejected, Recalled]
```

#### G-A-002. ProcessArchived — Процесс в архиве

**Что проверяет.** Процесс в статусе Archived.

**Логика.**
```
process.status == Archived
```

---

## 5. Действия (Actions)

### 5.1 Действия процесса

#### A-P-001. AssignStageTasks — Назначить задачи этапа

**Что делает.** Создаёт задачи участникам активного этапа.

**Побочные эффекты.**
- Создание записей Participant.
- Публикация `approval.task.assigned`.

#### A-P-002. SetStartedAt — Установить время старта

**Что делает.** Фиксирует `process.startedAt`.

#### A-P-003. SetCompletedAt — Установить время завершения

**Что делает.** Фиксирует `process.completedAt` и устанавливает `autoArchiveScheduledAt = completedAt + 180 дней`.

#### A-P-004. CancelAllTasks — Отменить все задачи

**Что делает.** Отзывает все активные задачи процесса.

**Побочные эффекты.**
- Participant → Cancelled.
- Публикация `approval.task.revoked`.

#### A-P-005. NotifyParticipants — Уведомить участников

**Что делает.** Публикует событие `approval.notification.requested`.

**Параметры:**
- `recipients` — категория получателей
- `template` — шаблон уведомления

#### A-P-006. ResetStageStatuses — Сбросить статусы этапов

**Что делает.** Возвращает этапы в статус Pending.

#### A-P-007. ScheduleAutoArchive — Запланировать автоархивацию

**Что делает.** Устанавливает `autoArchiveScheduledAt`.

**Параметры:**
- `days: int` — 180

#### A-P-008. ArchiveProcess — Архивировать процесс

**Что делает.** Переводит процесс в Archived.

**Побочные эффекты.**
- Статус → Archived.
- Сохранение previousStatus.
- Отмена активных задач.
- Публикация `approval.process.auto-archived`.

#### A-P-009. RestoreProcess — Восстановить процесс

**Что делает.** Возвращает из архива в previousStatus.

**Побочные эффекты.**
- Статус → previousStatus.
- Сброс `autoArchiveScheduledAt = now + 180 дней`.
- Публикация `approval.process.restored`.

#### A-P-010. PublishDomainEvent — Опубликовать доменное событие

**Что делает.** Публикует событие в Outbox.

**Параметры:**
- `eventType: string`
- `payload: JSON`

---

### 5.2 Действия этапа

#### A-S-001. AssignParticipantTasks — Назначить задачи участникам

**Что делает.** Создаёт задачи участникам этапа в текущей итерации. Поведение зависит от `stage.executionOrder`.

**Логика.**
```
if stage.executionOrder == Parallel (по умолчанию, включая null):
    ∀ participant ∈ stage.participants:
        participant.status = Assigned
        participant.assignedAt = now

else if stage.executionOrder == Sequential:
    first = stage.participants.minBy(orderIdx)
    first.status = Assigned
    first.assignedAt = now

    ∀ остальные participant ∈ stage.participants:
        participant.status = Pending
```

**Побочные эффекты.**
- Публикация `approval.task.assigned` для каждого участника, переведённого в `Assigned`.

**Примечание.** При `Sequential` задачу сразу получает только первый по `orderIdx` участник. Остальные ожидают в состоянии `Pending`; они переводятся в `Assigned` действием `AssignNextParticipantTask` (A-S-009) по мере того, как предыдущие участники принимают решение или автосогласуются.

#### A-S-002. SetStageStartedAt — Установить время старта этапа

**Что делает.** Фиксирует `stage.startedAt`.

#### A-S-003. CalcStageDueAt — Рассчитать срок этапа

**Что делает.** Вычисляет `stage.dueAt = stage.startedAt + stage.duration`.

#### A-S-004. CloseIteration — Закрыть итерацию

**Что делает.** Переводит текущую итерацию в Completed.

#### A-S-005. AutoApproveMissing — Автосогласовать незавершивших

**Что делает.** Всем участникам без решения присваивает статус AutoApproved.

#### A-S-006. ActivateNextStage — Активировать следующий этап

**Что делает.** Активирует следующий этап в маршруте.

**Логика.**
1. Проверить, есть ли у этапа предыдущие итерации.
2. Если есть — создать новую (инкремент `iterationIdx`).
3. Иначе — создать Iter 1.
4. Назначить задачи участникам (`AssignParticipantTasks`, с учётом `executionOrder`).

#### A-S-007. CancelStageTasks — Отменить задачи этапа

**Что делает.** Отзывает все задачи этапа.

#### A-S-008. ClearActiveRemarks — Очистить активные замечания

**Что делает.** Переводит все Active и InProgress замечания этапа в AutoProcessed или Closed.

#### A-S-009. AssignNextParticipantTask — Назначить задачу следующему участнику (Sequential)

**Что делает.** После того как участник принял решение (`Decide`) или был автосогласован (`AutoApprove`), для этапов с `executionOrder = Sequential` находит следующего ожидающего участника и переводит его в `Assigned`. Для `executionOrder = Parallel` — no-op.

**Логика.**
```
if stage.executionOrder != Sequential:
    return   // no-op для Parallel

next = stage.participants
    .filter(status == Pending)
    .minBy(orderIdx)

if next != null:
    next.status = Assigned
    next.assignedAt = now
```

**Где вызывается.** Как действие переходов `Decide` и `AutoApprove` в `ParticipantStateMachine` (см. `04_state_machines.md` §7.2).

**Побочные эффекты.**
- `Participant.status = Assigned` для следующего участника.
- Публикация `approval.task.assigned`.

**Идемпотентность.** Если очередь пуста (`next == null`) — действие не делает ничего; повторный вызов безопасен.

---

### 5.3 Действия возврата на этап

#### A-RT-001. SupersedeStagesFrom — Пометить этапы заменёнными

**Что делает.** Помечает все этапы с `orderIdx > target` как SUPERSEDED.

**Логика.**
```
∀ stage where stage.orderIdx > targetStage.orderIdx:
    ∀ activeIteration ∈ stage.iterations where status == Active:
        iteration.status = Superseded
    stage.status = Pending
```

#### A-RT-002. RejectStagesFrom — Пометить активные этапы отклонёнными

**Что делает.** Помечает активные этапы с `orderIdx > target` как Rejected.

**Логика.**
```
∀ stage where stage.orderIdx > targetStage.orderIdx:
    if stage.status == Active:
        stage.status = Rejected
```

#### A-RT-003. CreateNewIterationFor — Создать новую итерацию

**Что делает.** Создаёт новую итерацию для целевого этапа.

**Логика.**
```
targetStage.iterations.maxIdx != null
    ? iterationIdx = maxIdx + 1
    : iterationIdx = 1

Create StageIteration(targetStage, iterationIdx, status=Active)
```

#### A-RT-004. ResetTargetStage — Сбросить целевой этап

**Что делает.** Переводит целевой этап в Pending и подготавливает к активации.

#### A-RT-005. ActivateTargetStage — Активировать целевой этап

**Что делает.** Активирует целевой этап с новой итерацией.

**Логика.**
1. Stage.status → Active.
2. AssignParticipantTasks.
3. SetStageStartedAt.
4. CalcStageDueAt.

---

### 5.4 Действия участника

#### A-PA-001. RecordDecision — Зафиксировать решение

**Что делает.** Сохраняет решение участника.

**Параметры:**
- `decision: Approve | ApproveWithComments | Reject`
- `comment: string?`

**Побочные эффекты.**
- Заполнение `participant.decision`.
- Создание Decision.
- Публикация `approval.decision.recorded`.

#### A-PA-002. RecordAutoDecision — Зафиксировать авторешение

**Что делает.** Сохраняет автосогласование.

**Убрано (ADR-024):** `A-PA-003 UpdateAssignee`. Выполнял замену участника на заместителя и публиковал `approval.task.reassigned` — функциональность замещений участников убрана из модуля целиком, см. `11_adr.md` ADR-024 и `06_event_contract.md` §20.

#### A-PA-004. RevokeParticipant — Отозвать участника

**Что делает.** Переводит участника в Revoked.

#### A-PA-005. CancelParticipant — Отменить участника

**Что делает.** Переводит участника в Cancelled.

---

### 5.5 Действия иерархии доп. согласующих

#### A-H-001. CreateChildAdditionalApprover — Создать дочернего доп. согласующего

**Что делает.** Создаёт нового доп. согласующего с родителем.

**Параметры:**
- `parentAdditionalApproverId: UUID`
- `userId: UUID`
- `organizationId: UUID?`
- `dueOffset: H0 | H3 | H8`

**Побочные эффекты.**
- Создание AdditionalApprover с `assignedBy = ADDITIONAL_APPROVER`.
- `parentAdditionalApproverId` заполнен.
- Публикация `approval.task.assigned`.

**Правила.**
- Глубина иерархии не ограничена.
- Количество не ограничено.
- Дочерний получает срок ≤ срока родителя.

#### A-H-002. SetLevel — Установить уровень в иерархии

**Что делает.** Вычисляет `level = parent.level + 1`.

**Логика.**
```
if parentAdditionalApproverId == null:
    level = 1
else:
    level = parent.level + 1
```

#### A-H-003. NotifyParent — Уведомить родителя

**Что делает.** Уведомляет родителя о новом дочернем доп. согласующем.

#### A-H-004. AutoRevokeChildren — Автоотозвать детей

**Что делает.** При закрытии этапа отзывает всех дочерних доп. согласующих.

---

### 5.6 Действия ревизий

#### A-RV-001. CreateRevisionProcess — Создать процесс ревизии

**Что делает.** Создаёт новый процесс для новой ревизии.

**Параметры:**
- `documentAggregateId: UUID`
- `revisionLabel: string`
- `newEntityRef: EntityRef`
- `templateId: UUID`
- `interruptPrevious: boolean`

**Побочные эффекты.**
- Создание нового ProcessInstance.
- Связывание с агрегатом через `documentAggregateId`.
- Связывание с предыдущим через `parentProcessId`.
- Публикация `approval.process.revision-created`.

#### A-RV-002. LinkToAggregate — Связать с агрегатом

**Что делает.** Устанавливает `documentAggregateId` и `parentProcessId`.

#### A-RV-003. InterruptPreviousProcess — Прервать предыдущий процесс

**Что делает.** Переводит предыдущий процесс в Recalled.

**Условие.** `interruptPrevious == true`.

**Логика.**
```
previousProcess.status = Recalled
```

#### A-RV-004. StartNewRevision — Запустить процесс новой ревизии

**Что делает.** Переводит новый процесс в Draft (готов к запуску).

---

### 5.7 Действия замечания

#### A-R-001. MarkActivated — Пометить действующим

**Что делает.** Переводит замечание в Active.

#### A-R-002. MarkNotRequiredFlag — Пометить «не требуется»

**Что делает.** Переводит замечание в NotRequired.

#### A-R-003. MarkAutoProcessed — Пометить авто-обработанным

**Что делает.** Переводит замечание в AutoProcessed.

#### A-R-004. MarkResolved — Пометить исправленным

**Что делает.** Переводит замечание в Resolved.

#### A-R-005. MarkRejected — Пометить отклонённым

**Что делает.** Переводит замечание в Rejected.

**Параметры:**
- `reason: string`

#### A-R-006. MarkClosed — Закрыть замечание

**Что делает.** Переводит замечание в Closed.

#### A-R-007. TakeIntoWork — Взять в работу

**Что делает.** Переводит замечание из Active в InProgress.

---

### 5.8 Действия финального решения

#### A-FD-001. PersistFinalDecision — Сохранить финальное решение

**Что делает.** Создаёт запись FinalDecision.

**Параметры:**
- `result: Approve | ApproveWithComments | Reject`
- `comment: string?`

**Побочные эффекты.**
- Создание FinalDecision.
- Публикация `approval.unified.final-decision.recorded`.

---

### 5.9 Служебные действия

#### A-UTIL-001. LogAuditEvent — Зафиксировать событие аудита

**Что делает.** Создаёт запись AuditEvent.

**Параметры:**
- `entityType: string`
- `entityId: UUID`
- `action: string`
- `actorId: UUID?`
- `payload: JSON`

#### A-UTIL-002. ValidateRemarkComment — Проверить комментарий

**Что делает.** Проверяет, что комментарий указан для ApproveWithComments и Reject.

---

## 6. Регистрация и область применения

### 6.1 Как регистрируются

```java
GuardRegistry.register("IsInitiator", IsInitiatorGuard)
GuardRegistry.register("AllMandatorySlotsFilled", AllMandatorySlotsFilledGuard)
...

ActionRegistry.register("AssignStageTasks", AssignStageTasksAction)
ActionRegistry.register("SupersedeStagesFrom", SupersedeStagesFromAction)
...
```

### 6.2 Область применения

| Область | Описание |
|---|---|
| **GLOBAL** | Встроенные условия и действия |
| **CUSTOM** | Пользовательские |

### 6.3 Жизненный цикл

1. **Разработка** — пишется код.
2. **Регистрация** — добавление в реестр с кодом и метаданными.
3. **Использование** — ссылка из карты переходов.
4. **Публикация** — проверка, что все ссылки валидны.
5. **Работа** — вызов во время перехода.

---

## 7. Валидация

### 7.1 При публикации карты переходов

| Проверка | Что проверяется |
|---|---|
| Все guards существуют | Каждый guard в transition.guards зарегистрирован |
| Все actions существуют | Каждый action в transition.actions зарегистрирован |
| Все emits валидны | Каждый emit — известный тип события |
| Схема параметров | Параметры guards/actions соответствуют схеме |

### 7.2 Ошибки

| Ошибка | Описание |
|---|---|
| `GUARD_NOT_FOUND` | Guard не зарегистрирован |
| `ACTION_NOT_FOUND` | Action не зарегистрирован |
| `PARAMS_INVALID` | Параметры не соответствуют схеме |
| `EVENT_UNKNOWN` | Неизвестный тип события |

---

## 8. Точки расширения

### 8.1 Добавление своего условия

```java
class MyCustomGuard : Guard {
    override fun evaluate(context: GuardContext): Boolean {
        return ...
    }
}

GuardRegistry.register(
    code = "MyCustomGuard",
    displayName = "Моё условие",
    description = "...",
    handler = MyCustomGuard(),
    scope = Scope.CUSTOM
)
```

### 8.2 Добавление своего действия

```java
class MyCustomAction : Action {
    override fun execute(context: ActionContext) {
        ...
    }
}

ActionRegistry.register(
    code = "MyCustomAction",
    displayName = "Моё действие",
    description = "...",
    handler = MyCustomAction(),
    scope = Scope.CUSTOM
)
```

### 8.3 Ограничения

| Ограничение | Причина |
|---|---|
| Custom guards/actions не могут менять состояние напрямую | Изоляция |
| Custom guards/actions не могут публиковать доменные события | Изоляция |
| Custom guards/actions должны быть идемпотентны | Надёжность |
| Custom guards/actions должны работать в рамках транзакции | Атомарность |

---

## 9. Примеры использования

### 9.1 Запуск процесса

```
{
  "code": "StartProcess",
  "from": "Draft",
  "to": "InProgress",
  "trigger": "userAction",
  "guards": ["IsInitiator", "AllMandatorySlotsFilled", "AllDurationsValid"],
  "actions": ["AssignStageTasks", "SetStartedAt", "PublishDomainEvent"],
  "emits": ["approval.process.started"]
}
```

### 9.2 Возврат на этап

```
{
  "code": "ResumeProcess",
  "from": "OnRework",
  "to": "InProgress",
  "trigger": "userAction",
  "guards": ["IsInitiator", "AllActiveRemarksHandled", "IsValidReturnTarget"],
  "actions": [
    "SupersedeStagesFrom",
    "RejectStagesFrom",
    "CreateNewIterationFor",
    "ActivateTargetStage",
    "PublishDomainEvent"
  ],
  "emits": ["approval.process.resumed", "approval.stage.activated"]
}
```

### 9.3 Создание ревизии

```
{
  "code": "CreateRevision",
  "from": "*",
  "to": "Draft",
  "trigger": "userAction",
  "guards": ["IsInitiator", "ProcessIterationEnabled"],
  "actions": [
    "CreateRevisionProcess",
    "LinkToAggregate",
    "InterruptPreviousProcess",
    "PublishDomainEvent"
  ],
  "emits": ["approval.process.revision-created"]
}
```

### 9.4 Добавление дочернего доп. согласующего

```
{
  "code": "AddChildAdditionalApprover",
  "from": "*",
  "to": "*",
  "trigger": "userAction",
  "guards": ["IsAdditionalApprover", "StageNotClosed"],
  "actions": [
    "CreateChildAdditionalApprover",
    "SetLevel",
    "NotifyParent",
    "PublishDomainEvent"
  ],
  "emits": ["approval.task.assigned"]
}
```

### 9.5 Архивация

```
{
  "code": "AutoArchiveApproved",
  "from": "Approved",
  "to": "Archived",
  "trigger": "timer",
  "guards": ["AutoArchiveTimeReached"],
  "actions": ["ArchiveProcess", "PublishDomainEvent"],
  "emits": ["approval.process.auto-archived"]
}
```

### 9.6 Решение участника (Sequential)

```
{
  "code": "Decide",
  "from": ["Assigned", "InProgress"],
  "to": "Decided",
  "trigger": "userAction",
  "guards": ["IsParticipant", "HasValidDecision"],
  "actions": ["RecordDecision", "AssignNextParticipantTask", "PublishDomainEvent"],
  "emits": ["approval.decision.recorded"]
}
```

**Примечание.** `AssignNextParticipantTask` — no-op для `executionOrder = Parallel`; выполняет назначение только при `Sequential`.

---

## 10. Сводная таблица

### 10.1 Guards

| Код | Название | Категория | Сущность |
|---|---|---|---|
| G-P-001 | IsInitiator | AUTHORIZATION | Процесс |
| G-P-002 | IsResponsible | AUTHORIZATION | Процесс |
| G-P-003 | IsAdmin | AUTHORIZATION | Процесс |
| G-P-004 | AllMandatorySlotsFilled | VALIDATION | Процесс |
| G-P-005 | AllDurationsValid | VALIDATION | Процесс |
| G-P-006 | AllActiveRemarksHandled | VALIDATION | Процесс |
| G-P-007 | AllStagesApproved | LIFECYCLE | Процесс |
| G-P-008 | AllStagesApprovedWithComments | LIFECYCLE | Процесс |
| G-P-009 | AnyStageRejected | LIFECYCLE | Процесс |
| G-P-010 | AllStagesCompleted | LIFECYCLE | Процесс |
| G-P-011 | ProcessIterationEnabled | REVISION | Процесс |
| G-S-001 | PreviousStageCompleted | LIFECYCLE | Этап |
| G-S-002 | AggregationApproved | LIFECYCLE | Этап |
| G-S-003 | AggregationApprovedWithComments | LIFECYCLE | Этап |
| G-S-004 | AggregationRework | LIFECYCLE | Этап |
| G-S-005 | DueDateExpired | TIMER | Этап |
| G-S-006 | ProcessRecalled | LIFECYCLE | Этап |
| G-S-007 | ReturnToPriorStage | ITERATION | Этап |
| G-PA-001 | IsParticipant | AUTHORIZATION | Участник |
| G-PA-002 | HasValidDecision | VALIDATION | Участник |
| G-PA-003 | StageClosed | LIFECYCLE | Участник |
| G-PA-004 | IterationSuperseded | ITERATION | Участник |
| G-PA-006 | IsResponsibleAsParticipant | AUTHORIZATION | Участник |
| G-PA-007 | CanAssignTask | LIFECYCLE | Участник |
| G-RT-001 | IsValidReturnTarget | ITERATION | Возврат |
| G-RT-002 | HasMultipleReturnTargets | ITERATION | Возврат |
| G-H-001 | IsMainApprover | AUTHORIZATION | Иерархия |
| G-H-002 | IsAdditionalApprover | AUTHORIZATION | Иерархия |
| G-H-003 | IsParentAdditionalApprover | HIERARCHY | Иерархия |
| G-R-001 | ParticipantNotDecided | VALIDATION | Замечание |
| G-R-002 | HasReason | VALIDATION | Замечание |
| G-R-003 | ApproveOrApproveWithComments | LIFECYCLE | Замечание |
| G-FD-001 | FinalDecisionPending | LIFECYCLE | Финальное решение |
| G-FD-002 | CanAcceptFinalDecision | AUTHORIZATION | Финальное решение |
| G-A-001 | AutoArchiveTimeReached | TIMER | Архивация |
| G-A-002 | ProcessArchived | LIFECYCLE | Архивация |

**Убрано (ADR-024):** `G-PA-005 SubstitutionActive` — см. §4.3.

### 10.2 Actions

| Код | Название | Категория | Сущность |
|---|---|---|---|
| A-P-001 | AssignStageTasks | TASK | Процесс |
| A-P-002 | SetStartedAt | DATA | Процесс |
| A-P-003 | SetCompletedAt | DATA | Процесс |
| A-P-004 | CancelAllTasks | TASK | Процесс |
| A-P-005 | NotifyParticipants | NOTIFICATION | Процесс |
| A-P-006 | ResetStageStatuses | LIFECYCLE | Процесс |
| A-P-007 | ScheduleAutoArchive | TIMER | Процесс |
| A-P-008 | ArchiveProcess | LIFECYCLE | Процесс |
| A-P-009 | RestoreProcess | LIFECYCLE | Процесс |
| A-P-010 | PublishDomainEvent | NOTIFICATION | Общее |
| A-S-001 | AssignParticipantTasks | TASK | Этап |
| A-S-002 | SetStageStartedAt | DATA | Этап |
| A-S-003 | CalcStageDueAt | DATA | Этап |
| A-S-004 | CloseIteration | LIFECYCLE | Этап |
| A-S-005 | AutoApproveMissing | LIFECYCLE | Этап |
| A-S-006 | ActivateNextStage | LIFECYCLE | Этап |
| A-S-007 | CancelStageTasks | TASK | Этап |
| A-S-008 | ClearActiveRemarks | LIFECYCLE | Этап |
| A-S-009 | AssignNextParticipantTask | TASK | Участник |
| A-RT-001 | SupersedeStagesFrom | ITERATION | Возврат |
| A-RT-002 | RejectStagesFrom | ITERATION | Возврат |
| A-RT-003 | CreateNewIterationFor | ITERATION | Возврат |
| A-RT-004 | ResetTargetStage | ITERATION | Возврат |
| A-RT-005 | ActivateTargetStage | ITERATION | Возврат |
| A-PA-001 | RecordDecision | DATA | Участник |
| A-PA-002 | RecordAutoDecision | DATA | Участник |
| A-PA-004 | RevokeParticipant | LIFECYCLE | Участник |
| A-PA-005 | CancelParticipant | LIFECYCLE | Участник |
| A-H-001 | CreateChildAdditionalApprover | HIERARCHY | Иерархия |
| A-H-002 | SetLevel | HIERARCHY | Иерархия |
| A-H-003 | NotifyParent | HIERARCHY | Иерархия |
| A-H-004 | AutoRevokeChildren | HIERARCHY | Иерархия |
| A-RV-001 | CreateRevisionProcess | REVISION | Ревизия |
| A-RV-002 | LinkToAggregate | REVISION | Ревизия |
| A-RV-003 | InterruptPreviousProcess | REVISION | Ревизия |
| A-RV-004 | StartNewRevision | REVISION | Ревизия |
| A-R-001 | MarkActivated | LIFECYCLE | Замечание |
| A-R-002 | MarkNotRequiredFlag | LIFECYCLE | Замечание |
| A-R-003 | MarkAutoProcessed | LIFECYCLE | Замечание |
| A-R-004 | MarkResolved | LIFECYCLE | Замечание |
| A-R-005 | MarkRejected | LIFECYCLE | Замечание |
| A-R-006 | MarkClosed | LIFECYCLE | Замечание |
| A-R-007 | TakeIntoWork | LIFECYCLE | Замечание |
| A-FD-001 | PersistFinalDecision | DATA | Финальное решение |
| A-UTIL-001 | LogAuditEvent | AUDIT | Общее |

**Убрано (ADR-024):** `A-PA-003 UpdateAssignee` — см. §5.4.
| A-UTIL-002 | ValidateRemarkComment | VALIDATION | Общее |

---
