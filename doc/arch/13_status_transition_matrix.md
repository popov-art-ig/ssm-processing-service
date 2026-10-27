# Сводная матрица статусов и переходов

**Версия:** 1.0<br>
**Дата:** 2026-10-27<br>
**Назначение:** единая картина жизненных циклов всех сущностей модуля «Согласование»: кто инициирует переход, из какого статуса в какой, при каких условиях и с какими действиями.<br>
**Основание:** `01_glossary.md`, `02_process_types.md`, `03_domain_model.md`, `04_state_machines.md`, `05_guards_actions_registry.md`, `06_event_contract.md`, `08_db_schema.md`; фактическая реализация — миграции `V18`–`V25` и код `service/guard`, `service/action`.

---

## 1. Как читать документ

### 1.1. Что входит в матрицу

В матрицу включены все сущности, у которых есть собственный жизненный цикл (state machine):

| № | Сущность | `entityType` | Машина состояний |
|---|---|---|---|
| 1 | Экземпляр процесса | `PROCESS` | `ProcessStateMachine` |
| 2 | Этап процесса | `STAGE` | `StageStateMachine` |
| 3 | Итерация этапа | `STAGE_ITERATION` | `StageIterationStateMachine` |
| 4 | Участник этапа | `PARTICIPANT` | `ParticipantStateMachine` |
| 5 | Дополнительный согласующий | `ADDITIONAL_APPROVER` | `AdditionalApproverStateMachine` |
| 6 | Финальное решение | `FINAL_DECISION` | `FinalDecisionStateMachine` |
| 7 | Замечание | `REMARK` | `RemarkStateMachine` |
| 8 | Версия шаблона маршрута | `TEMPLATE` | `LifecycleStatus` (общий жизненный цикл версии) |
| 9 | Версия карты переходов | `—` (статус самой строки конфига) | `LifecycleStatus` (общий жизненный цикл версии) |

**Не входят:** `Decision`, `Comment`, `AuditEvent`, `ArchiveMetadata` — у них нет статусов и переходов; `ProcessIteration` — вычисляемое представление (`max(iterationIdx)`), физически не хранится.

### 1.2. Колонки

| Колонка | Смысл |
|---|---|
| **№** | Сквозной идентификатор строки; префикс соответствует сущности |
| **Объект** | Сущность и, где применимо, тип процесса: `PROCESS / STANDARD`, `PROCESS / UNIFIED`, `STAGE / STANDARD`, `STAGE / UNIFIED` |
| **Переход** | Код перехода в `transition_config`; в скобках — альтернативное имя из спецификации, если код в реализации отличается |
| **Инициатор · триггер** | Кто инициирует: роль пользователя и тип триггера — 👤 `USER_ACTION`, ⚙️ `SYSTEM_ACTION`, ⏱ `TIMER` |
| **Из** | Начальный статус; для начального состояния сущности — `—` |
| **В** | Конечный статус; `Previous` — восстановление в `previousStatus` |
| **Описание** | Бизнес-смысл перехода в одном предложении |
| **Условия и проверки** | Guards, проверки прав, инварианты и предусловия. Коды `G-*` — по `05_guards_actions_registry.md`; в скобках «факт.» — как проверка реализована сейчас, если она отличается от спецификации |
| **Действия и эффекты** | Actions (`A-*`), изменения полей, события, уведомления. `AuditEvent` пишется движком на **каждый выполненный** переход и в таблице не повторяется. События указаны по контракту `emits`; физическая публикация в Outbox/RabbitMQ пока не реализована (см. §6.12) |
| **Реализ.** | Состояние в текущей базе кода (см. §1.3) |

### 1.3. Обозначения реализации

| Маркер | Значение |
|---|---|
| ✅ | Переход засеян в `transition_config` (миграции `V18`–`V25`) и обслуживается зарегистрированными guard/action-бинами |
| 🟡 | Переход реализован частично или иначе, чем в целевой карте; отклонение описано в ячейке или в §6 |
| ⬜ | Переход описан в архитектуре (`04_state_machines.md`), но в `transition_config` и коде отсутствует |
| ➕ | Фактический переход, которого нет в целевой карте `04_state_machines.md`; добавлен реализацией |

### 1.4. Роли

| Роль | Кто это | Что инициирует |
|---|---|---|
| **Инициатор** | Создатель процесса | Запуск, отзыв, возобновление, выбор целевого этапа, ревизия |
| **Ответственный** | Ответственный UNIFIED-процесса | Согласование маршрута, финальное решение; может быть участником этапа |
| **Участник** | Основной согласующий или подписант этапа | Решение `Approve` / `ApproveWithComments` / `Reject`, замечания, комментарии |
| **Доп. согласующий** | Дополнительный согласующий любого уровня | Рекомендация, замечания, комментарии, добавление дочернего доп. согласующего |
| **Администратор** | Администратор модуля | Публикация, депрекация и архивация шаблонов и карт; восстановление из архива |
| **Система** | `TransitionEngine` / action, актор `SYSTEM` | Переходы по итогам агрегации, вложенные переходы, закрытие/отмена задач |
| **Таймер** | Планировщик, актор `TIMER` | Переходы по `dueAt` и `autoArchiveScheduledAt` |

### 1.5. Важная оговорка

Таблица объединяет **целевую карту** (`04_state_machines.md`, `05_guards_actions_registry.md`) и **фактическое состояние** (`V18`–`V25`). Там, где они расходятся, приоритет для понимания бизнес-логики — у целевой карты, для понимания того, что работает сейчас, — у маркеров реализации и §6. Это сделано намеренно: документ должен показывать и «как задумано», и «где мы находимся», не смешивая одно с другим.

Если в `04_state_machines.md` / `05_guards_actions_registry.md` действия перехода не детализированы, в таблице приведён минимально необходимый набор действий по смыслу перехода. Такие ячейки — проект, а не утверждённая спецификация; для точной реализации сверяйтесь с §6 и тикетами.

---

## 2. Статусы по сущностям

| Объект | Начальный статус | Все статусы | Терминальные | Комментарий |
|---|---|---|---|---|
| `PROCESS / STANDARD` | `Draft` | `Draft`, `InProgress`, `OnRework`, `Recalled`, `Approved`, `ApprovedWithComments`, `Archived` | `Archived` | Статусов `Rejected`, `PendingResponsibleApproval`, `AwaitingFinalDecision` у STANDARD нет: итог определяется последним этапом, отклонение ведёт на доработку |
| `PROCESS / UNIFIED` | `Draft` | `Draft`, `PendingResponsibleApproval`, `InProgress`, `AwaitingFinalDecision`, `Recalled`, `Approved`, `ApprovedWithComments`, `Rejected`, `Archived` | `Archived` | `OnRework` у UNIFIED нет: возврата на доработку нет, итог определяет финальное решение |
| `STAGE / STANDARD` | `Pending` | `Pending`, `Active`, `Approved`, `ApprovedWithComments`, `OnRework`, `Rejected`, `AutoApproved`, `Cancelled` | `Approved`, `ApprovedWithComments`, `Rejected`, `AutoApproved`, `Cancelled` | `OnRework` — не закрытый этап: возможна новая итерация. `Rejected` — только при возврате на более ранний этап |
| `STAGE / UNIFIED` | `Pending` | `Pending`, `Active`, `Completed`, `Cancelled` | `Completed`, `Cancelled` | Итерация у UNIFIED одна и невидима пользователю |
| `STAGE_ITERATION` | `Active` (по спецификации) | `Active`, `Completed`, `Superseded`, `Cancelled` | `Completed`, `Superseded`, `Cancelled` | Фактически при создании пишется `Pending`, а статусы не зарегистрированы в `status_registry` (DEFECT-07) |
| `PARTICIPANT` | `Assigned` (Parallel) / `Pending` (Sequential) | `Pending`, `Assigned`, `InProgress`, `Decided`, `AutoApproved`, `Revoked`, `Cancelled` | `Decided`, `AutoApproved`, `Revoked`, `Cancelled` | `Pending` — только для `executionOrder = Sequential`; для `Parallel` все стартуют в `Assigned` |
| `ADDITIONAL_APPROVER` | `Assigned` | `Assigned`, `InProgress`, `Recommended`, `AutoRevoked`, `Removed` | `Recommended`, `AutoRevoked`, `Removed` | Рекомендация не влияет на статус этапа и на агрегацию (ADR-016) |
| `FINAL_DECISION` | `Pending` | `Pending`, `Decided` | `Decided` | Решение immutable: после `Decided` переходов нет |
| `REMARK` | `Proposed` (от доп.) / `Active` (от основного) | `Proposed`, `Active`, `InProgress`, `NotRequired`, `AutoProcessed`, `Resolved`, `Rejected`, `Closed` | `NotRequired`, `AutoProcessed`, `Resolved`, `Rejected`, `Closed` | Целевая модель. Фактически используются `Draft`, `Active`, `InProgress`, `Processed`, `Rejected`, `Proposed`, `NotRequired` (см. §6.5) |
| `TEMPLATE` | `DRAFT` | `DRAFT`, `PUBLISHED`, `DEPRECATED`, `ARCHIVED` | `ARCHIVED` | Коды в БД — верхним регистром (`LifecycleStatus`); в тексте спецификации — `Draft`/`Published`/… |
| `STATE_MACHINE_CONFIG` | `DRAFT` | `DRAFT`, `PUBLISHED`, `DEPRECATED`, `ARCHIVED` | `ARCHIVED` | Жизненный цикл версии карты; сейчас карты засеяны сразу в `PUBLISHED`, редактор версий отсутствует |

**Сквозные правила статусов**

- Любое изменение статуса — только через `TransitionEngine` и явный переход (ADR-002/ADR-028); прямые записи статуса — исключение, которое должно быть описано и обосновано.
- Переход выполняется по ключу `(entityType, fromState, trigger, processType)`; при нескольких кандидатах — сортировка по `priority` (по убыванию), затем по коду перехода.
- `is_terminal` в `state_config` и `status_registry` — признак для UI/валидации; фактические правила закрытия сущности задаются переходами, а не только этим флагом.
- Каждый выполненный переход пишет `AuditEvent`; события из `emits` возвращаются в `TransitionResult`, но физическая публикация в Outbox/RabbitMQ пока не реализована (§6.12).

---

## 3. Единая матрица переходов

> Таблица читается сверху вниз: сначала процесс, затем его этапы, итерации, участники, доп. согласующие, финальное решение, замечания и версии конфигурации. Внутри каждого объекта переходы упорядочены по жизненному циклу.

| № | Объект | Переход | Инициатор · триггер | Из | В | Описание | Условия и проверки | Действия и эффекты | Реализ. |
|---|---|---|---|---|---|---|---|---|---|
| P-S-01 | `PROCESS / STANDARD` | `StartProcess` | Инициатор · 👤 | `Draft` | `InProgress` | Запуск согласования: маршрут зафиксирован, этапы пошли в работу | `IsInitiator` (G-P-001)<br>`AllMandatorySlotsFilled` (G-P-004; факт. упрощённая проверка: у обязательного этапа есть участник)<br>`AllDurationsValid` (G-P-005)<br>шаблон `PUBLISHED`; маршрут не пуст | `AssignStageTasks` — активирует первый этап вложенным `ActivateStage`<br>`SetStartedAt`<br>event `approval.process.started` | ✅ |
| P-S-02 | `PROCESS / STANDARD` | `ProcessRework`<br>(в 04 — `StageRejected`) | Система · ⚙️<br>после агрегации этапа | `InProgress` | `OnRework` | Этап ушёл на доработку — процесс переходит на доработку | `HasStageOnRework` (в 04 — `AnyStageRejected`, G-P-009): есть этап `OnRework`<br>процесс `InProgress` | `EvaluateProcessRework`<br>event `approval.process.rework-required` | ✅ |
| P-S-03 | `PROCESS / STANDARD` | `ResumeProcess` | Инициатор · 👤 | `OnRework` | `InProgress` | Возобновление после обработки замечаний с выбором целевого этапа | `IsInitiator` (G-P-001)<br>`AllActiveRemarksHandled` (G-P-006; факт. `AllRemarksProcessed` — проверяет `Open`/`InProgress`, не `Active`)<br>`IsValidReturnTarget` (G-RT-001; факт. `IsTargetStageAllowed`: `target.orderIdx <= OnRework.orderIdx`, `allowedReturnStages` не читается)<br>передан `targetStageOrderIdx` | `RejectStagesFrom` (A-RT-002): этапы между целевым и ушедшим на доработку → `Rejected`<br>`ActivateTargetStage` (A-RT-005): создаёт новую итерацию (A-RT-003 внутри), клонирует участников, активирует этап и назначает задачи<br>events `approval.process.resumed`, `approval.stage.activated`<br>факт.: `SupersedeStagesFrom` (A-RT-001) не реализован, старые итерации не закрываются | 🟡 |
| P-S-04 | `PROCESS / STANDARD` | `ProcessApproved`<br>(в 04 — `AllStagesApproved`) | Система · ⚙️<br>после закрытия последнего этапа | `InProgress` | `Approved` | Все этапы завершены без замечаний | `AllStagesCompleted` (G-P-010): все этапы `Approved` / `ApprovedWithComments` / `Completed`<br>`HasComments` = `false` (иначе сработает приоритетный `ProcessApprovedWithComments`) | `CompleteProcess` (реализует A-P-003): только `completedAt`<br>event `approval.process.completed`<br>факт.: `ScheduleAutoArchive` (A-P-007) не вызывается | ✅ |
| P-S-05 | `PROCESS / STANDARD` | `ProcessApprovedWithComments`<br>(в 04 — `AllStagesApprovedWithComments`) | Система · ⚙️ | `InProgress` | `ApprovedWithComments` | Все этапы завершены, хотя бы один — с замечаниями | `AllStagesCompleted` (G-P-010)<br>`HasComments` = `true`<br>приоритет 10 выше `ProcessApproved` | `CompleteProcess` (реализует A-P-003): только `completedAt`<br>event `approval.process.completed`<br>факт.: `ScheduleAutoArchive` (A-P-007) не вызывается | ✅ |
| P-S-06 | `PROCESS / STANDARD` | `RecallProcess` | Инициатор · 👤 | `InProgress`, `OnRework` | `Recalled` | Отзыв процесса инициатором до завершения | `IsInitiator` (G-P-001)<br>`IsRecallRequest`: в параметрах передан `reason` (отличает отзыв от `ResumeProcess` из `OnRework`) | event `approval.process.recalled`<br>в спецификации — `CancelAllTasks` (A-P-004), в карте действий сейчас отсутствует | ✅<br>без отмены задач |
| P-S-07 | `PROCESS / STANDARD` | `ReworkFromRecalled` | Инициатор · 👤 | `Recalled` | `Draft` | Вернуть отозванный процесс в черновик для повторного запуска | `IsInitiator` (G-P-001)<br>процесс `Recalled` | `ResetStageStatuses` (A-P-006): этапы → `Pending`<br>отмена активных задач | ⬜ |
| P-S-08 | `PROCESS / STANDARD` | `AutoArchive*`<br>(`AutoArchiveApproved`, `AutoArchiveApprovedWithComments`, `AutoArchiveRecalled`) | Таймер · ⏱<br>180 дней | `Approved`, `ApprovedWithComments`, `Recalled` | `Archived` | Автоархивация завершённого процесса | `AutoArchiveTimeReached` (G-A-001): `now >= autoArchiveScheduledAt`<br>`autoArchiveEnabled = true`<br>статус входит в список архивируемых | `ArchiveProcess` (A-P-008): `Archived`, сохранение `previousStatus`, `archivedAt`<br>`CancelAllTasks` (A-P-004)<br>event `approval.process.auto-archived`<br>процесс становится read-only | ⬜ |
| P-S-09 | `PROCESS / STANDARD` | `RestoreFromArchive` | Администратор · 👤 | `Archived` | `Previous` | Восстановление процесса из архива | `IsAdmin` (G-P-003)<br>`ProcessArchived` (G-A-002)<br>`previousStatus` заполнен | `RestoreProcess` (A-P-009): статус = `previousStatus`, `autoArchiveScheduledAt = now + 180 дней`, `restoredAt` / `restoredBy`<br>event `approval.process.restored` | ⬜ |
| P-U-01 | `PROCESS / UNIFIED` | `SubmitRoute` | Инициатор · 👤 | `Draft` | `PendingResponsibleApproval` | Отправить маршрут на согласование ответственному | `IsInitiator` (G-P-001)<br>`processType = UNIFIED`<br>маршрут сформирован; ответственный назначен; обязательные слоты и сроки валидны<br>`requiresResponsibleApproval = true` (при выключенном флаге отдельный переход не описан — см. §8) | фиксация маршрута<br>уведомление ответственному<br>event `approval.unified.route.submitted` | ⬜ |
| P-U-02 | `PROCESS / UNIFIED` | `ApproveRoute` | Ответственный · 👤 | `PendingResponsibleApproval` | `InProgress` | Согласовать маршрут и запустить процесс | `IsResponsible` (G-P-002)<br>процесс в `PendingResponsibleApproval`<br>маршрут валиден; ответственный назначен | `SetStartedAt` (A-P-002)<br>`AssignStageTasks` (A-P-001) — активация первого этапа<br>event `approval.unified.route.approved`<br>уведомление инициатору | ⬜ |
| P-U-03 | `PROCESS / UNIFIED` | `RejectRoute` | Ответственный · 👤 | `PendingResponsibleApproval` | `Draft` | Вернуть маршрут инициатору без запуска | `IsResponsible` (G-P-002)<br>процесс в `PendingResponsibleApproval`<br>причина или комментарий (бизнес-требование) | event `approval.unified.route.rejected`<br>уведомление инициатору<br>маршрут снова редактируемый | ⬜ |
| P-U-04 | `PROCESS / UNIFIED` | `RecallFromPending`<br>`RecallFromProgress` | Инициатор · 👤 | `PendingResponsibleApproval`, `InProgress` | `Recalled` | Отозвать процесс до финального решения | `IsInitiator` (G-P-001)<br>`processType = UNIFIED`<br>финальное решение ещё не принято | `CancelAllTasks` (A-P-004)<br>event `approval.process.recalled`<br>уведомление ответственному | ⬜ |
| P-U-05 | `PROCESS / UNIFIED` | `AllStagesCompleted` | Система · ⚙️<br>после закрытия последнего этапа | `InProgress` | `AwaitingFinalDecision` | Все этапы завершены, ожидается финальное решение | `AllStagesCompleted` (G-P-010): все этапы `Completed`<br>`processType = UNIFIED`<br>ответственный назначен | event `approval.unified.awaiting-final-decision`<br>уведомление ответственному<br>новые задачи не создаются | ⬜ |
| P-U-06 | `PROCESS / UNIFIED` | `FinalApprove` | Ответственный · 👤 | `AwaitingFinalDecision` | `Approved` | Финальное решение «Согласовать» | `IsResponsible` (G-P-002)<br>`CanAcceptFinalDecision` (G-FD-002): процесс `AwaitingFinalDecision`<br>`FinalDecision.result = Approve` | `PersistFinalDecision` (A-FD-001)<br>`CompleteProcess` (реализует A-P-003)<br>events `approval.unified.final-decision.recorded`, `approval.process.completed` | ⬜ |
| P-U-07 | `PROCESS / UNIFIED` | `FinalApproveWithComments` | Ответственный · 👤 | `AwaitingFinalDecision` | `ApprovedWithComments` | Финальное решение «Согласовать с замечаниями» | `IsResponsible` (G-P-002)<br>`CanAcceptFinalDecision` (G-FD-002)<br>результат `ApproveWithComments`<br>комментарий обязателен | `PersistFinalDecision` (A-FD-001)<br>`CompleteProcess` (реализует A-P-003)<br>events `approval.unified.final-decision.recorded`, `approval.process.completed` | ⬜ |
| P-U-08 | `PROCESS / UNIFIED` | `FinalReject` | Ответственный · 👤 | `AwaitingFinalDecision` | `Rejected` | Финальное решение «Отклонить» | `IsResponsible` (G-P-002)<br>`CanAcceptFinalDecision` (G-FD-002)<br>результат `Reject`<br>причина или комментарий обязателен | `PersistFinalDecision` (A-FD-001)<br>`CompleteProcess` (реализует A-P-003)<br>events `approval.unified.final-decision.recorded`, `approval.process.completed` | ⬜ |
| P-U-09 | `PROCESS / UNIFIED` | `ReworkFromRecalled` | Инициатор · 👤 | `Recalled` | `Draft` | Вернуть отозванный UNIFIED-процесс в черновик | `IsInitiator` (G-P-001)<br>финальное решение не принято<br>ревизии для UNIFIED не применимы | `ResetStageStatuses` (A-P-006)<br>отмена активных задач | ⬜ |
| P-U-10 | `PROCESS / UNIFIED` | `AutoArchive*`<br>(`AutoArchiveApproved`, `AutoArchiveApprovedWithComments`, `AutoArchiveRejected`, `AutoArchiveRecalled`) | Таймер · ⏱<br>180 дней | `Approved`, `ApprovedWithComments`, `Rejected`, `Recalled` | `Archived` | Автоархивация завершённого UNIFIED-процесса | `AutoArchiveTimeReached` (G-A-001)<br>`autoArchiveEnabled = true`<br>статус входит в список архивируемых | `ArchiveProcess` (A-P-008)<br>`CancelAllTasks` (A-P-004)<br>event `approval.process.auto-archived` | ⬜ |
| P-U-11 | `PROCESS / UNIFIED` | `RestoreFromArchive` | Администратор · 👤 | `Archived` | `Previous` | Восстановление UNIFIED-процесса из архива | `IsAdmin` (G-P-003)<br>`ProcessArchived` (G-A-002)<br>`previousStatus` заполнен | `RestoreProcess` (A-P-009)<br>event `approval.process.restored` | ⬜ |
| S-S-01 | `STAGE / STANDARD` | `ActivateStage` | Система · ⚙️<br>из `StartProcess` / `ActivateNextStage` / `ApproveRoute` | `Pending` | `Active` | Активировать этап: назначить задачи и рассчитать срок | `PreviousStageCompleted` (G-S-001): предыдущий этап `Approved` / `ApprovedWithComments` / `Completed`; для первого этапа — `true`<br>процесс `InProgress`; участники назначены | `AssignParticipantTasks` (A-S-001): `Parallel` — все `Assigned`; `Sequential` — первый `Assigned`, остальные `Pending`<br>`SetStageStartedAt` (A-S-002)<br>`CalcStageDueAt` (A-S-003)<br>event `approval.stage.activated` | ✅<br>новая итерация при обычной активации не создаётся |
| S-S-02 | `STAGE / STANDARD` | `StageApproved` | Система · ⚙️<br>после `EvaluateAggregation` | `Active` | `Approved` | Агрегация решений: «Согласовано» | `AllParticipantsDecided` (факт.)<br>`AggregationApproved` (G-S-002): `evaluateAggregation = APPROVED` по `decisionMode` (`AND`, `ANY_APPROVE`, `ANY_DECISION`) | `ActivateNextStage` (A-S-006)<br>`EvaluateProcessCompletion`<br>event `approval.stage.completed`<br>`CloseIteration` в карте действий отсутствует | ✅ |
| S-S-03 | `STAGE / STANDARD` | `StageApprovedWithComments` | Система · ⚙️ | `Active` | `ApprovedWithComments` | Агрегация решений: «Согласовано с замечаниями» | `AllParticipantsDecided` (факт.)<br>`AggregationApprovedWithComments` (G-S-003): `evaluateAggregation = APPROVED_WITH_COMMENTS`<br>приоритет 10 выше `StageApproved` | `ActivateNextStage` (A-S-006)<br>`EvaluateProcessCompletion`<br>event `approval.stage.completed` | ✅ |
| S-S-04 | `STAGE / STANDARD` | `StageOnRework` | Система · ⚙️ | `Active` | `OnRework` | Агрегация: «На доработку»; этап не закрыт, возможна новая итерация | `AllParticipantsDecided` (факт.)<br>`AggregationRework` (G-S-004): `evaluateAggregation = REWORK` — `Reject` в `AND` / `FIRST_REJECT_FAIL_FAST` / `ANY_REJECT`, либо первое решение `Reject` в `ANY_DECISION`<br>терминальный `Rejected` здесь не ставится | `EvaluateProcessRework` — процесс `InProgress` → `OnRework`<br>event `approval.stage.rejected`<br>этап остаётся открытым для новой итерации | ✅ |
| S-S-05 | `STAGE / STANDARD` | `StageAutoApproved` | Таймер · ⏱<br>`dueAt` | `Active` | `AutoApproved` | Истёк срок этапа: этап автосогласован | `DueDateExpired` (G-S-005): `now > dueAt`<br>процесс не `Recalled`<br>есть участники без решения | `AutoApproveMissing` (A-S-005): всем без решения — `AutoApproved`<br>`CloseIteration` (A-S-004)<br>event `approval.stage.auto-approved`<br>далее `ActivateNextStage` / `EvaluateProcessCompletion` | ⬜ |
| S-S-06 | `STAGE / STANDARD` | `CancelStage` | Система · ⚙️<br>по отзыву процесса | `Active` | `Cancelled` | Отменить активный этап при отзыве процесса | `ProcessRecalled` (G-S-006): процесс `Recalled` | `CancelStageTasks` (A-S-007): отзыв задач<br>event `approval.stage.cancelled` | ⬜ |
| S-S-07 | `STAGE / STANDARD` | `StageRejectedByReturn` | Система · ⚙️<br>по действию процесса `A-RT-002` | `Active` | `Rejected` | Закрыть этап как отклонённый, если цель возврата выше него | `ReturnToPriorStage` (G-S-007): `returnTargetStage.orderIdx < currentStage.orderIdx`<br>инициируется только из `ResumeProcess`; не пользовательский триггер | полный цикл перехода: запись статуса, `AuditEvent`, события<br>факт.: `RejectStagesFrom` пишет `Rejected` напрямую, без перехода | 🟡 |
| S-S-08 | `STAGE / STANDARD` | `StageResume`<br>(факт. `ReactivateStage`) | Система · ⚙️<br>из `ActivateTargetStage`; инициатор — 👤 в `ResumeProcess` | `OnRework` | `Active` | Возобновить этап с новой итерацией после доработки | `IsInitiator` — на уровне процесса<br>`NewIterationCreated`<br>целевой этап допустим (`IsValidReturnTarget` / `IsTargetStageAllowed`)<br>факт.: guard у самого перехода отсутствует | `ActivateTargetStage` (A-RT-005) создаёт новую итерацию (A-RT-003 внутри) и клонирует участников<br>`AssignParticipantTasks` (A-S-001)<br>`SetStageStartedAt` (A-S-002)<br>`CalcStageDueAt` (A-S-003)<br>event `approval.stage.activated` | ✅ |
| S-S-09 | `STAGE / STANDARD` | `ReactivateStage` | Система · ⚙️<br>из `ActivateTargetStage` | `Approved`, `ApprovedWithComments` | `Active` | Вернуть ранее согласованный этап в работу: новая итерация, повторное согласование | guard отсутствует<br>допустимость цели проверена на уровне `ResumeProcess`<br>предыдущая итерация должна стать `Superseded` | `AssignParticipantTasks` (A-S-001)<br>`SetStageStartedAt` (A-S-002)<br>`CalcStageDueAt` (A-S-003)<br>event `approval.stage.activated`<br>факт.: предыдущая итерация не закрывается (DEFECT-07) | ➕ ✅ |
| S-U-01 | `STAGE / UNIFIED` | `ActivateStageUnified` | Система · ⚙️ | `Pending` | `Active` | Активировать следующий этап UNIFIED | `PreviousStageCompleted` (G-S-001): предыдущий этап `Completed`<br>все этапы проходятся последовательно<br>участники назначены | `AssignParticipantTasks` (A-S-001): все параллельно<br>`SetStageStartedAt` (A-S-002)<br>`CalcStageDueAt` (A-S-003)<br>event `approval.stage.activated` | ⬜ |
| S-U-02 | `STAGE / UNIFIED` | `StageCompleted` | Система · ⚙️ | `Active` | `Completed` | Этап завершён: все участники высказались | все участники `Decided` / `AutoApproved`<br>решения участников не влияют на движение<br>этап не `Cancelled` | `ActivateNextStage` (A-S-006)<br>`EvaluateProcessCompletion`<br>event `approval.stage.completed` | ⬜ |
| S-U-03 | `STAGE / UNIFIED` | `StageAutoCompleted` | Таймер · ⏱<br>`dueAt` | `Active` | `Completed` | Истёк срок: этап завершён автоматически | `DueDateExpired` (G-S-005)<br>незавершившие участники переводятся в `AutoApproved` | `AutoApproveMissing` (A-S-005)<br>`CloseIteration` (A-S-004)<br>event `approval.stage.auto-approved` / `approval.stage.completed` | ⬜ |
| S-U-04 | `STAGE / UNIFIED` | `CancelStageUnified` | Система · ⚙️<br>по отзыву процесса | `Active` | `Cancelled` | Отменить этап при отзыве процесса | `ProcessRecalled` (G-S-006) | `CancelStageTasks` (A-S-007)<br>event `approval.stage.cancelled` | ⬜ |
| SI-01 | `STAGE_ITERATION` | `CompleteIteration` | Система · ⚙️ | `Active` | `Completed` | Круг согласования завершён вместе с этапом | этап закрыт: `Approved` / `ApprovedWithComments` / `Completed` / `AutoApproved` | `CloseIteration` (A-S-004): `completedAt`<br>event `approval.stage-iteration.completed` | ⬜ |
| SI-02 | `STAGE_ITERATION` | `SupersedeIteration` | Система · ⚙️ | `Active` | `Superseded` | Итерация заменена новой при возврате или повторной активации | `ReworkInitiated`: создаётся новая итерация того же этапа | `SupersedeStagesFrom` (A-RT-001) / `CreateNewIterationFor` (A-RT-003)<br>event `approval.stage-iteration.superseded`<br>факт.: предыдущая итерация не закрывается (DEFECT-07) | ⬜ |
| SI-03 | `STAGE_ITERATION` | `CancelIteration` | Система · ⚙️<br>по отзыву процесса | `Active` | `Cancelled` | Итерация отменена при отзыве процесса | `ProcessRecalled` (G-S-006) | `CancelStageTasks` (A-S-007)<br>отмена итерации<br>отдельного события в контракте нет | ⬜ |
| PA-01 | `PARTICIPANT` | `AssignTask` | Система · ⚙️<br>`A-S-001` / `A-S-009` | `Pending` | `Assigned` | Выдать задачу следующему участнику при последовательном порядке | `CanAssignTask` (G-PA-007): `executionOrder = Sequential`; участник `Pending`; все участники с меньшим `orderIdx` — `Decided` / `AutoApproved` | `AssignNextParticipantTask` (A-S-009)<br>event `approval.task.assigned`<br>факт.: статус ставится действием напрямую, перехода в карте нет | ⬜ |
| PA-02 | `PARTICIPANT` | `StartWork` | Участник · 👤 | `Assigned` | `InProgress` | Взять задачу в работу (открыть карточку) | `IsParticipant` (G-PA-001)<br>статус `Assigned`<br>итерация `Active` | отдельного события нет<br>фиксируется `AuditEvent` | ⬜ |
| PA-03 | `PARTICIPANT` | `Decide` | Участник (`APPROVER` / `SIGNER`) · 👤 | `Assigned`, `InProgress` | `Decided` | Зафиксировать решение: `Approve` / `ApproveWithComments` / `Reject` | `IsParticipant` (G-PA-001; факт. `IsAssignedParticipant`)<br>`HasValidDecision` (G-PA-002): результат из трёх значений; комментарий для `ApproveWithComments`; причина или замечание для `Reject`<br>этап `Active`<br>факт.: переход только из `Assigned`; `HasValidDecision` не зарегистрирован — комментарий проверяет `DecisionService`, значение `decisionType` не валидируется | `RecordDecision` (A-PA-001)<br>`RecordComment`<br>`EvaluateAggregation` — может сразу закрыть этап<br>event `approval.decision.recorded`<br>факт.: `AssignNextParticipantTask` (A-S-009) в карте не вызывается — Sequential-очередь не продвигается | 🟡 |
| PA-04 | `PARTICIPANT` | `AutoApprove` | Таймер · ⏱<br>`dueAt` | `Assigned`, `InProgress` | `AutoApproved` | Срок истёк — участник автосогласован | `DueDateExpired` (G-S-005)<br>решение ещё не принято<br>этап `Active`; процесс не `Recalled` | `RecordAutoDecision` (A-PA-002)<br>`AssignNextParticipantTask` (A-S-009)<br>`EvaluateAggregation`<br>event `approval.task.expired` | ⬜ |
| PA-05 | `PARTICIPANT` | `RevokeParticipant` | Система · ⚙️ | `Assigned`, `InProgress` | `Revoked` | Задача отозвана: этап закрыт или итерация заменена | `StageClosed` (G-PA-003) **или** `IterationSuperseded` (G-PA-004)<br>этап в `Approved` / `ApprovedWithComments` / `Rejected` / `AutoApproved` / `Completed` / `Cancelled` | `RevokeParticipant` (A-PA-004)<br>event `approval.task.revoked` | ⬜ |
| PA-06 | `PARTICIPANT` | `CancelParticipant` | Система · ⚙️<br>по отзыву процесса | `Assigned`, `InProgress` | `Cancelled` | Задача отменена при отзыве процесса | `ProcessRecalled` (G-S-006) | `CancelParticipant` (A-PA-005)<br>event `approval.task.revoked` | ⬜ |
| AA-01 | `ADDITIONAL_APPROVER` | `StartWork` | Доп. согласующий · 👤 | `Assigned` | `InProgress` | Взять задачу в работу | `IsAdditionalApprover` (G-H-002)<br>статус `Assigned`<br>этап `Active` | отдельного события нет<br>фиксируется `AuditEvent` | ⬜ |
| AA-02 | `ADDITIONAL_APPROVER` | `Recommend` | Доп. согласующий · 👤 | `Assigned`, `InProgress` | `Recommended` | Дать рекомендацию «Согласовать» или «Отклонить»; на итог не влияет | `IsAdditionalApprover` (G-H-002)<br>статус `Assigned` / `InProgress`<br>рекомендация заполнена; замечание или комментарий | сохранение `recommendation`<br>event `approval.additional-approver.recommended`<br>в агрегации не участвует (ADR-016) | ⬜ |
| AA-03 | `ADDITIONAL_APPROVER` | `AutoRevoke` | Система · ⚙️<br>после решения основного | `Assigned`, `InProgress` | `AutoRevoked` | Основной согласующий решил — задачи доп. согласующих автоотозваны | `MainApproverDecided`: основной участник `Decided`<br>этап закрывается | `AutoRevokeChildren` (A-H-004)<br>event `approval.additional-approver.revoked` | ⬜ |
| AA-04 | `ADDITIONAL_APPROVER` | `Remove` | Пользователь с правом удаления · 👤 | `Assigned`, `InProgress` | `Removed` | Удалить доп. согласующего до завершения его задачи | `ProcessInRecalledOrOnRework`<br>`isDeletable = true`<br>статус `Assigned` / `InProgress` | event `approval.additional-approver.removed`<br>уведомление родителю | ⬜ |
| FD-01 | `FINAL_DECISION` | `RecordFinalDecision` | Ответственный · 👤 | `Pending` | `Decided` | Зафиксировать финальное решение UNIFIED; решение immutable | `IsResponsible` (G-P-002)<br>`CanAcceptFinalDecision` (G-FD-002): процесс `AwaitingFinalDecision`<br>результат из трёх значений; комментарий для `ApproveWithComments` / `Reject` | `PersistFinalDecision` (A-FD-001)<br>event `approval.unified.final-decision.recorded`<br>инициирует переход процесса P-U-06…P-U-08<br>дальнейших переходов нет | ⬜ |
| R-01 | `REMARK` | `ActivateRemark` | Основной согласующий · 👤 | `Proposed` | `Active` | Активировать замечание доп. согласующего | `IsMainApprover` (G-H-001)<br>`ParticipantNotDecided` (G-R-001): основной ещё не принял решение<br>замечание `Proposed` | `MarkActivated` (A-R-001)<br>event `approval.remark.activated` | ⬜<br>факт.: см. R-08 |
| R-02 | `REMARK` | `MarkNotRequired` | Основной согласующий · 👤 | `Proposed` | `NotRequired` | Признать замечание не требующим реакции | `IsMainApprover` (G-H-001; факт. в карте — `IsParticipant`)<br>замечание `Proposed` | смена статуса движком<br>`NotifyRemarkStatusChange`<br>event `approval.remark.marked-not-required`<br>факт.: action `MarkNotRequiredFlag` (A-R-002) не зарегистрирован | ✅ |
| R-03 | `REMARK` | `AutoProcessRemark` | Система · ⚙️<br>при закрытии этапа | `Proposed` | `AutoProcessed` | Неактивированное замечание авто-обработано при закрытии этапа | `StageClosed` (G-PA-003)<br>замечание `Proposed` | `MarkAutoProcessed` (A-R-003)<br>event `approval.remark.auto-processed` | ⬜ |
| R-04 | `REMARK` | `TakeIntoWork` | Инициатор · 👤 | `Active` | `InProgress` | Взять замечание в работу | `IsInitiator`; факт. в карте — `IsRemarkAssignee`<br>замечание `Active` | смена статуса движком<br>`NotifyRemarkStatusChange`<br>event `approval.remark.activated` (факт.)<br>факт.: action `TakeIntoWork` (A-R-007) не зарегистрирован | ✅ |
| R-05 | `REMARK` | `ResolveRemark` | Инициатор · 👤 | `Active`, `InProgress` | `Resolved` | Отметить замечание исправленным | `IsInitiator`; факт. в карте — `IsRemarkResolution`<br>замечание `Active` / `InProgress`<br>указано, что именно исправлено (resolution / комментарий) | `MarkResolved` (A-R-004)<br>event `approval.remark.resolved` | 🟡<br>факт.: только `InProgress → Processed`; статуса `Resolved` нет |
| R-06 | `REMARK` | `RejectRemark` | Инициатор · 👤 | `Active`, `InProgress` | `Rejected` | Отклонить замечание с обоснованием | `IsInitiator`<br>`HasReason` (G-R-002; факт. в карте — `IsRemarkRejection`: передан `reason`): `rejectionReason` не пуст<br>замечание `Active` / `InProgress` | `MarkRejected` (A-R-005)<br>event `approval.remark.rejected` | 🟡<br>факт.: только `InProgress → Rejected` |
| R-07 | `REMARK` | `CloseRemark` | Система · ⚙️<br>при положительном решении | `Active`, `InProgress` | `Closed` | Закрыть замечание при согласовании этапа | `ApproveOrApproveWithComments` (G-R-003): основной принял положительное решение<br>замечание `Active` / `InProgress` | `MarkClosed` (A-R-006)<br>event `approval.remark.closed` | ⬜ |
| R-08 | `REMARK` | `CreateRemark` | Участник · 👤<br>факт. | `Draft` | `Active` | Создание замечания: сразу действующее | `IsParticipant` (факт.)<br>создаётся в `Draft` | `AssignRemarkToAuthor`<br>`NotifyRemarkStatusChange`<br>event `approval.remark.created` | ➕ ✅ |
| R-09 | `REMARK` | `ProcessRemark` | Инициатор · 👤<br>факт. | `InProgress` | `Processed` | Обработать замечание; фактическая замена целевого `Resolved` | `IsRemarkResolution`: передан `resolution`<br>замечание `InProgress` | `NotifyRemarkStatusChange`<br>event `approval.remark.resolved` | ➕ ✅ |
| T-01 | `TEMPLATE` | `PublishTemplate` | Администратор · 👤 | `DRAFT` | `PUBLISHED` | Опубликовать версию шаблона | `AllMandatorySlotsValid`: у каждого обязательного слота есть `userId` или `acceptableRoles`<br>`ValidateTemplateStructure`: корректная структура этапов и слотов<br>версия в `DRAFT`<br>опубликованная версия неизменяема: правка = новая версия | `ValidateTemplateStructure`<br>`SetPublishedTimestamp`<br>event `approval.config.published`<br>`ConfigAuditEvent PUBLISHED` | ✅ |
| T-02 | `TEMPLATE` | `DeprecateTemplate` | Администратор · 👤 | `PUBLISHED` | `DEPRECATED` | Снять версию с публикации: новые процессы не создаются, начатые продолжаются | шаблон `PUBLISHED`<br>по спецификации — нет активных процессов (сейчас `NoActiveProcesses` не проверяется)<br>Fork &amp; Drain: начатые процессы продолжают по снимку | event `approval.config.deprecated`<br>`ConfigAuditEvent DEPRECATED` | ✅<br>без проверки активных процессов (DEFECT-14) |
| T-03 | `TEMPLATE` | `ArchiveTemplate` | Администратор · 👤 | `DEPRECATED` | `ARCHIVED` | Вывести версию из оборота окончательно | `NoActiveProcesses`: `activeProcessCount = 0`<br>шаблон `DEPRECATED` | `SetArchivedTimestamp`<br>`ConfigAuditEvent ARCHIVED`<br>события архивации нет: тип не описан в контракте | ✅ |
| C-01 | `STATE_MACHINE_CONFIG` | `PublishConfig` | Администратор · 👤 | `DRAFT` | `PUBLISHED` | Опубликовать карту переходов; она становится активной для новых процессов | ровно одно состояние `isInitial`<br>все `fromState` / `toState` существуют<br>все guards и actions зарегистрированы<br>все `emits` — известные события<br>из каждого нефинального состояния есть исходящий переход<br>нет конфликтов `(entityType, fromState, trigger, priority)`<br>нет удалённых используемых состояний<br>версия не пустая | `TransitionMapValidator`<br>`ConfigAuditEvent PUBLISHED`<br>`publishedAt` / `publishedBy` | ⬜<br>карты засеяны миграциями; редактора версий нет |
| C-02 | `STATE_MACHINE_CONFIG` | `DeprecateConfig` | Администратор · 👤 | `PUBLISHED` | `DEPRECATED` | Снять карту с публикации: новые процессы идут по новой версии, активные продолжают | карта `PUBLISHED`<br>Fork &amp; Drain: активные процессы не переключаются | `ConfigAuditEvent DEPRECATED`<br>event `approval.config.deprecated` | ⬜ |
| C-03 | `STATE_MACHINE_CONFIG` | `ArchiveConfig` | Администратор · 👤 | `DEPRECATED` | `ARCHIVED` | Вывести версию карты из оборота | `activeProcessCount = 0`<br>карта `DEPRECATED` | `ConfigAuditEvent ARCHIVED`<br>версия недоступна для новых процессов | ⬜ |

**Итог по объёму:** в таблице 62 строки, из них 3 — фактические переходы вне целевой карты (`S-S-09`, `R-08`, `R-09`), то есть 59 целевых строк. В `transition_config` засеяно 23 уникальные строки (20 уникальных кодов; в SQL 26 value-строк, потому что V23 дважды вставляет три `ReactivateStage` — `on conflict do nothing`). Полностью не засеяны пять машин: `PROCESS / UNIFIED`, `STAGE / UNIFIED`, `STAGE_ITERATION`, `ADDITIONAL_APPROVER`, `FINAL_DECISION`. Жизненный цикл `STATE_MACHINE_CONFIG` существует только как модель.

---

## 4. Как сущности складываются в один сценарий

### 4.1. STANDARD: успешное согласование

```
StartProcess (P-S-01)
  → ActivateStage этапа 1 (S-S-01)
  → AssignParticipantTasks: участники Assigned
  → Decide каждого участника (PA-03)
  → EvaluateAggregation
  → StageApproved (S-S-02)
  → ActivateNextStage → ActivateStage этапа 2 (S-S-01)
  → … последний этап …
  → ProcessApproved (P-S-04)
  → AutoArchive через 180 дней (P-S-08)
```

### 4.2. STANDARD: отклонение и возврат

```
Reject на этапе N
  → StageOnRework (S-S-04)
  → ProcessRework (P-S-02)
  → инициатор обрабатывает замечания
  → ResumeProcess (P-S-03), выбран target
      → SupersedeStagesFrom: этапы после target → Pending
      → RejectStagesFrom: Active-этапы после target → Rejected (S-S-07)
      → CreateNewIterationFor + ActivateTargetStage
  → ReactivateStage / StageResume (S-S-08, S-S-09)
  → повторное согласование с этапа target
```

### 4.3. STANDARD: отзыв

```
InProgress | OnRework
  → RecallProcess (P-S-06)
  → Recalled
      → ReworkFromRecalled (P-S-07) → Draft
      → AutoArchive (P-S-08) → Archived
```

### 4.4. UNIFIED: маршрут и финальное решение

```
SubmitRoute (P-U-01)
  → PendingResponsibleApproval
  → ApproveRoute (P-U-02) | RejectRoute (P-U-03)
  → InProgress
  → этапы Completed (S-U-01…S-U-04)
  → AllStagesCompleted (P-U-05)
  → AwaitingFinalDecision
  → FinalApprove / FinalApproveWithComments / FinalReject (P-U-06…P-U-08)
  → Approved | ApprovedWithComments | Rejected → Archived (P-U-10)
```

### 4.5. Таймеры

```
dueAt этапа истёк
  → StageAutoApproved / StageAutoCompleted (S-S-05, S-U-03)
  → AutoApprove участников без решения (PA-04)
  → агрегация / завершение этапа

autoArchiveScheduledAt наступил
  → AutoArchive процесса (P-S-08, P-U-10)
```

### 4.6. Иерархия дополнительных согласующих

```
основной участник / инициатор / шаблон
  → создаётся AdditionalApprover в Assigned
  → StartWork (AA-01)
  → Recommend (AA-02) — рекомендация, не решение
  → основной принимает решение
  → AutoRevoke детей (AA-03)
```

---

## 5. Сводные инварианты

| Инвариант | Формулировка |
|---|---|
| Snapshot-on-Start | Процесс не меняет версию карты и шаблона после старта; `templateRef`, `configVersion` неизменяемы |
| Идемпотентность | Повторный триггер не меняет статус, если переход уже выполнен или не прошёл guards |
| Транзакционность | Смена статуса, actions и запись `AuditEvent` — в одной транзакции |
| События после commit | Проектный инвариант: публикация через Outbox после фиксации транзакции; в текущей реализации не подключена (§6.12) |
| AuditEvent | Пишется на каждый выполненный переход; отклонённый переход отдельной записи не создаёт |
| Итерации этапа | При активации этапа, у которого уже есть итерации, создаётся новая; лимита нет |
| `ProcessIteration` | Вычисляется как `max(stageIteration.iterationIdx)`, не хранится |
| `Rejected` этапа | Только при возврате на более ранний этап; при доработке того же этапа — `OnRework` |
| `OnRework` этапа | Этап не закрыт; возможна новая итерация и возврат в `Active` |
| Финальное решение | Immutable; после `Decided` переходов нет |
| `allowedReturnStages` | Только прошлые и текущий этапы; без дублей; не задан — возврат только на текущий |
| Возврат на этап | Работает всегда, независимо от `processIterationEnabled`; ревизии — только при `true` |
| Ревизия документа | Новый процесс, а не переход существующего; связь через `documentAggregateId` и `parentProcessId` |
| Автоархивация | Через 180 дней после завершения; восстанавливает только администратор |
| Snapshot версии | Старые версии шаблонов и карт живут, пока на них есть активные процессы (Fork & Drain) |
| Публикация версии | Только администратор; изменения — только additive; удаление — при `activeProcessCount = 0` |
| Рекомендация доп. согласующего | Не участвует в агрегации и не меняет статус этапа/процесса (ADR-016) |

---

## 6. Расхождения целевой карты и реализации

Ниже — то, что важно знать при чтении матрицы и при приёмке. Это не «список всех дефектов», а расхождения именно по статусам и переходам; полные тикеты — в `doc/tickets/DEFECTS-INDEX.md`.

### 6.1. Коды и имена переходов

| Целевая карта (`04_state_machines.md`) | Фактически (`V18`–`V25`) |
|---|---|
| `StageRejected` (`InProgress → OnRework`) | `ProcessRework` |
| `AllStagesApproved`, `AllStagesApprovedWithComments` | `ProcessApproved`, `ProcessApprovedWithComments` |
| `AnyStageRejected` | `HasStageOnRework` |
| `AllStagesApproved` (guard) | `AllStagesCompleted` + `HasComments` |
| `StageResume` | `ReactivateStage` (три `from_state`: `OnRework`, `Approved`, `ApprovedWithComments`) |
| `IsParticipant` | `IsAssignedParticipant` |
| `IsValidReturnTarget` | `IsTargetStageAllowed` (не читает `allowedReturnStages`) |
| `AllActiveRemarksHandled` | `AllRemarksProcessed` (проверяет `Open`/`InProgress`, а замечания создаются в `Active`) |

### 6.2. Возврат этапа в `Rejected`

В целевой карте §5.2 и ADR-025 переход `StageRejectedByReturn` должен быть полноценным переходом `StageStateMachine`, который инициируется действием процесса `A-RT-002 RejectStagesFrom`. Фактически `RejectStagesFromAction` **присваивает статус `Rejected` напрямую** (см. Javadoc класса), без отдельного перехода, guard `ReturnToPriorStage` не вызывается, событие `approval.stage.rejected` для этих этапов не публикуется. Это осознанное упрощение, зафиксированное в коде, но оно расходится с принципом «любое изменение статуса — только через переход» и с ADR-025. Связанный тикет — DEFECT-07.

### 6.3. Возврат на ранее согласованный этап

В целевой карте §5.2 есть только `StageResume` из `OnRework`, но по бизнес-логике возврат возможен на любой этап `≤` текущего, включая уже согласованные. Реализация добавила `ReactivateStage` из `Approved` и `ApprovedWithComments` — это **правильнее** целевой таблицы, но сама целевая таблица этого не описывает. Предложение: добавить эти `from_state` в `04_state_machines.md` и считать `StageResume`/`ReactivateStage` одним переходом с тремя начальными статусами.

### 6.4. Итерации этапа

- Статусы `STAGE_ITERATION` не зарегистрированы в `status_registry`; `TransitionEngine.validateTargetStatus` не может их проверить.
- `RouteGenerationService` создаёт итерацию со статусом `Pending`, `ActivateTargetStageAction` — со статусом `Active`; целевая модель начинается с `Active`.
- Предыдущая итерация при возврате не закрывается (`Completed` / `Superseded`) и не получает `completedAt`; `CloseIteration` в карте действий отсутствует.
- `CompleteIteration`, `SupersedeIteration`, `CancelIteration` не засеяны.

### 6.5. Замечания

Целевая модель: `Proposed → Active → InProgress → Resolved / Rejected / Closed`, плюс `NotRequired` и `AutoProcessed`.

Фактически:

- создание идёт `Draft → Active` (`CreateRemark`), статуса «создано основным» в целевой модели нет;
- обработка идёт `InProgress → Processed` (`ProcessRemark`), а не `→ Resolved`;
- статусов `Resolved`, `Closed`, `AutoProcessed` в `status_registry` нет;
- `ResolveRemark` и `RejectRemark` доступны только из `InProgress`, а не из `Active`/`InProgress`;
- `AllRemarksProcessed` проверяет `Open`/`InProgress`, поэтому не видит `Active`-замечания, и процесс может возобновиться с необработанными замечаниями.

Связанный тикет — DEFECT-01.

### 6.6. Участники

Фактически засеян только `Decide` (`Assigned → Decided`). Остальные статусы (`Pending`, `InProgress`, `AutoApproved`, `Revoked`, `Cancelled`) и переходы `AssignTask`, `StartWork`, `AutoApprove`, `RevokeParticipant`, `CancelParticipant` в карте отсутствуют. `AssignParticipantTasks` пишет статусы участников напрямую. `AssignNextParticipantTask` (A-S-009) не реализован: при `Sequential` первый участник получает задачу, а остальные остаются в `Pending` без продвижения очереди. `Decide` не работает из `InProgress`, хотя целевая карта это разрешает.

### 6.7. UNIFIED, итерации, доп. согласующие, финальное решение

Для `PROCESS / UNIFIED`, `STAGE / UNIFIED`, `STAGE_ITERATION`, `ADDITIONAL_APPROVER`, `FINAL_DECISION` в репозитории нет ни `state_machine_config`, ни `status_registry`-записей (кроме упомянутых точечно), ни переходов. Эти машины существуют только в архитектуре. Для UNIFIED нет даже статусов `AwaitingFinalDecision` и `Rejected` для `PROCESS`, а для `FINAL_DECISION` — статусов `Pending`/`Decided`.

### 6.8. Архивация и восстановление

`AutoArchive*` и `RestoreFromArchive` не реализованы: нет `@Scheduled`-задач, нет действий `ArchiveProcess` / `RestoreProcess`, нет статуса `Archived` для `PROCESS` в `status_registry`. При этом `Recalled` в V23 помечен `is_terminal = true`, что противоречит сценарию `Recalled → Archived` и `Recalled → Draft`. `AdminController` содержит заглушку восстановления.

### 6.9. Шаблоны

Жизненный цикл `TEMPLATE` реализован и засеян (V25). Отклонения:

- `CreateTemplate` как переход отсутствует: создание шаблона идёт через `TemplateService.create` напрямую в `DRAFT`;
- `DeprecateTemplate` не проверяет активные процессы, хотя спецификация этого требует;
- `fork → publish` не снимает прежнюю версию с публикации: возможны две `PUBLISHED` версии одной цепочки;
- событие архивации шаблона не описано в контракте, поэтому в `emits` перехода `ArchiveTemplate` его нет;
- `ConfigAuditEvent` для T-01…T-03 не пишется: сущность и `ConfigAuditAction` есть, но записей никто не создаёт; в таблице это проектный эффект.

Связанный тикет — DEFECT-14.

### 6.10. Карта переходов (StateMachineConfig)

Жизненный цикл `DRAFT → PUBLISHED → DEPRECATED → ARCHIVED` не реализован как state machine: миграции сразу создают `PUBLISHED`-конфиги. Есть только API чтения и валидации (`StateMachineConfigController`: `GET` и `POST /{configId}/validate`), редактора и публикации версий нет. Валидация карты (`TransitionMapValidator`) есть, но она не оформлена переходом `PublishConfig`.

### 6.11. Флаги терминальности

В `status_registry`/`state_config` фактические `is_terminal` местами расходятся с §2:

- `PROCESS.Recalled` — `true` (по сценарию архивации и возврата в `Draft` должен быть нетерминальным);
- `STAGE.Approved`, `STAGE.ApprovedWithComments`, `STAGE.Rejected` — `false` (по §5.1 для STANDARD они терминальные);
- `STAGE.Rejected` в V23 описан как «Отклонён при возврате», хотя в целевой модели это общий терминальный статус этапа.

### 6.12. События

`transition_config.emits` заполнены и возвращаются в `TransitionResult`, но физической публикации в Outbox/RabbitMQ нет: `TransitionConfig.emits` в текущей реализации только описывает контракт, а `EventPublisher`/Outbox-публикация относится к PHASE-19. Поэтому события в таблице — это **контракт события**, а не подтверждение фактической отправки. `AuditEvent` при этом действительно пишется в БД на каждый выполненный переход.

### 6.13. Агрегация решений

Условия `AggregationApproved` / `AggregationApprovedWithComments` / `AggregationRework` в таблице приведены по целевой функции `evaluateAggregation(stage)` (`05_guards_actions_registry.md` §4.2). Фактические guard-бины покрывают `decisionMode` неполно (DEFECT-02): `ANY_APPROVE` требует только `APPROVE`, `FIRST_REJECT_FAIL_FAST` ведёт себя как `AND`, `ANY_DECISION` при непустом списке решений всегда даёт `APPROVED`. Поэтому строки S-S-02…S-S-04 описывают целевое поведение, а не текущее качество агрегации.

---

## 7. Внестатусные команды

Эти операции создают сущности или меняют маршрут, но **не являются переходами статуса** и поэтому не вошли в основную таблицу.

| Команда | Кто | Что делает | Условия | Эффекты | Реализ. |
|---|---|---|---|---|---|
| `CreateProcess` | Инициатор / система | Создаёт `ProcessInstance` в `Draft` | найден подходящий шаблон; маршрут сгенерирован; слоты заполнены | `approval.process.created`; `templateRef`, `configVersion` зафиксированы | ✅ |
| `CreateRevision` | Инициатор | Создаёт новый процесс-ревизию в `Draft`; при `interruptPrevious = true` предыдущий процесс → `Recalled` | `processIterationEnabled = true`; `revisionLabel` уникален в агрегате | `approval.process.revision-created`; связь через `documentAggregateId`, `parentProcessId` | ⬜ |
| `CreateTemplate` | Администратор | Создаёт новую строку `Template` в `DRAFT` | корректные `processType`, этапы, слоты | `Template` в `DRAFT`; далее `PublishTemplate` | ✅<br>в обход движка |
| `ForkTemplate` / `createNewVersion` | Администратор | Создаёт новую версию шаблона `version + 1`, `parentTemplateId` | опубликованная версия неизменяема | новая строка `DRAFT`; прежняя версия должна стать `DEPRECATED` | ✅<br>Fork &amp; Drain неполный (DEFECT-14) |
| `CreateConfigVersion` | Администратор | Создаёт новую версию карты переходов | карта опубликована; изменения additive | новая строка `DRAFT`; публикация — `PublishConfig` | ⬜ |
| `AddChildAdditionalApprover` | Доп. согласующий | Создаёт дочернего доп. согласующего в `Assigned` | родитель активен; глубина и количество не ограничены | `CreateChildAdditionalApprover`, `SetLevel` (`level = parent.level + 1`), `NotifyParent`; `approval.additional-approver.assigned` | ⬜ |
| `AddAdditionalApprover` | Инициатор / основной участник | Создаёт доп. согласующего в `Assigned` | слот `ADDITIONAL_APPROVER`; `isDeletable`; срок ≤ срока родителя | `approval.additional-approver.assigned` | ⬜ |
| `CreateComment` | Участник / инициатор / ответственный | Создаёт комментарий | этап существует | `approval.comment.created`; статуса не имеет | ✅ |

---

## 8. Вопросы для ревью

1. **`StageRejectedByReturn`** — оставляем ли прямое присваивание `Rejected` в `RejectStagesFromAction` (как сейчас) или возвращаем полноценный переход по ADR-025? От этого зависит, будет ли `approval.stage.rejected` для промежуточных этапов и попадёт ли он в `audit_event` как переход.
2. **`ReactivateStage`** — добавляем ли `Approved` / `ApprovedWithComments` в `StageResume` целевой карты, или оставляем отдельный код перехода? Сейчас бизнес-логика (возврат на ранее согласованный этап) и целевая таблица расходятся.
3. **UNIFIED** — какие машины и статусы входят в ближайший релиз: только `PROCESS / UNIFIED` или сразу `STAGE / UNIFIED` и `FINAL_DECISION`? Сейчас в БД нет ни одной из них.
4. **Замечания** — какой набор статусов считаем целевым: `Resolved`/`Closed`/`AutoProcessed` из §10 или фактические `Processed`/`Rejected`/`NotRequired`? Нужен один источник истины, иначе `AllRemarksProcessed` и UI будут расходиться.
5. **Участник `InProgress`** — нужен ли он как отдельный статус, если задача открывается в UI, но решение фиксируется из `Assigned`? Если да, нужен переход `StartWork` и его guards.
6. **`is_terminal`** — что означает флаг: «нельзя изменить статус» или «нельзя создать задачу»? От ответа зависит, почему `Recalled` сейчас терминальный, а `Stage.Approved` — нет.
7. **Автоархивация** — включаем ли `ScheduleAutoArchive`/`ArchiveProcess`/`RestoreProcess` в ближайшую фазу; без них `Archived` и `RestoreFromArchive` остаются только на бумаге.
8. **`requiresResponsibleApproval = false`** — нужен ли для UNIFIED отдельный переход `Draft → InProgress` (без `PendingResponsibleApproval`)? В целевой карте его нет, а флаг шаблона допускает маршрут без согласования ответственным.

---

## 9. Связь с другими документами

| Документ | Что уточняет |
|---|---|
| `01_glossary.md` | Термины, русские названия статусов, роли |
| `02_process_types.md` | Различия STANDARD и UNIFIED, жизненные циклы, бизнес-правила |
| `03_domain_model.md` | Поля сущностей и инварианты |
| `04_state_machines.md` | Детальные карты состояний и переходов (источник целевой модели) |
| `05_guards_actions_registry.md` | Полный каталог guards и actions с логикой |
| `06_event_contract.md` | События, которые публикуют переходы |
| `07_api_contract.md` | REST-команды, инициирующие переходы |
| `08_db_schema.md` | `status_registry`, `state_machine_config`, `transition_config` |
| `DEFECTS-INDEX.md` | Тикеты по расхождениям, отмеченным в §6 |
