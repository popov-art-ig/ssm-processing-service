# PHASE-04 — Активация первого этапа (ActivateStage, AssignParticipantTasks)

> См. `doc/tickets/TEMPLATE.md` за описанием процесса. Тикет самодостаточен: весь материал
> скопирован ниже. Пометки вида «(источник: `05_guards_actions_registry.md` §4.2)» — это
> атрибуция для истории/ревью, не рабочие ссылки — у вас нет доступа к этим файлам.
>
> Подготовлен по итогам ревью Фазы 3 (PR #5, `e87bafa`, смержено без расхождений уровня ADR —
> см. «Ревью Cowork» в конце `doc/tickets/PHASE-03-process-service-start.md`). Объём выбран
> пользователем: «Активация первого этапа» — узкое продолжение того же вертикального среза,
> а не полный цикл согласования одного этапа (без `Decide`/агрегации решений — это следующая
> развилка).

## Статус

Не начато

## Контекст

Фаза 3 реализовала переход `StartProcess` (`Draft → InProgress`), но действие
`AssignStageTasks` (A-P-001) в нём было намеренно опущено — вместо `actions: ["AssignStageTasks",
"SetStartedAt"]` осталось только `actions: ["SetStartedAt"]` (см. `doc/tickets/
PHASE-03-process-service-start.md`, раздел 4: «Почему `AssignStageTasks`... не входят в эту
фазу»). Обоснование было: `AssignStageTasks` по смыслу требует активировать первый этап
маршрута через отдельный переход `StageStateMachine`, которого не было — движок `STAGE`-конфига
не заведён, `StageService`/guard/action-бины уровня этапа не существуют.

Эта фаза закрывает именно этот пробел: заводит минимальный `StageStateMachine` (только переход
`ActivateStage`, `Pending → Active`) и наконец реализует `AssignStageTasks` как настоящее
действие в переходе `StartProcess`, которое активирует первый этап маршрута и назначает
участникам задачи.

**Явно НЕ входит в эту фазу** (следующая развилка, за пределами «активации»): решение участника
(`Decide`, `ParticipantStateMachine`), агрегация решений (`AggregationApproved`/
`decisionMode`), закрытие этапа (`StageApproved`/`StageOnRework`), активация следующего этапа
(`ActivateNextStage`). Тикет той фазы должен ссылаться на эту фазу как на предпосылку.

## Источники (для истории, не для перехода)

- `04_state_machines.md` §5.1–5.2 (`StageStateMachine`, состояния и переходы STANDARD), §6.3
  (создание итераций при активации).
- `05_guards_actions_registry.md` §4.1 (условие `PreviousStageCompleted`, категория §4.2), §5.1
  (`AssignStageTasks`), §5.2 (`AssignParticipantTasks`, `SetStageStartedAt`, `CalcStageDueAt`),
  §5.3 (`ActivateTargetStage` — образец состава действий полной активации), §9.1 (пример JSON
  `StartProcess`, где `AssignStageTasks` — первое действие).
- `08_db_schema.md` §7 (`stage_instance`, поля `started_at`/`due_at`), §5.1
  (`status_registry`).
- `doc/tickets/PHASE-03-process-service-start.md` — контракт `ProcessService`/`TransitionEngine`,
  которые эта фаза использует как готовые компоненты, не меняя их логику.

## Объём фазы (Scope)

### 1. Что означает «активировать этап» — сведение спеки воедино

В таблице переходов `StageStateMachine` STANDARD (источник: `04_state_machines.md` §5.2)
столбца «Actions» нет — она перечисляет только `Guards`:

```
| Код          | From    | To     | Trigger      | Guards                  |
|--------------|---------|--------|--------------|-------------------------|
| ActivateStage| Pending | Active | systemAction | PreviousStageCompleted  |
```

Набор действий для «полной активации этапа» явно расписан только для случая возврата
(источник: `05_guards_actions_registry.md` §5.3, A-RT-005 `ActivateTargetStage`):

```
1. Stage.status → Active.
2. AssignParticipantTasks.
3. SetStageStartedAt.
4. CalcStageDueAt.
```

Это единственный конкретный образец того, что «активация этапа» означает в терминах actions.
Эта фаза берёт из него всё, кроме шага 1 (статус пишет сам `TransitionEngine` через
`statusWriter`, как и в предыдущих фазах) и кроме создания новой итерации (см. пункт 4 ниже —
отдельное упрощение). Итого `actions` перехода `ActivateStage` в этой фазе:
`["AssignParticipantTasks", "SetStageStartedAt", "CalcStageDueAt"]`.

`emits: ["approval.stage.activated"]` — по аналогии с `emits` перехода `ResumeProcess` в
примере §9.2 спеки, где `approval.stage.activated` — событие именно активации этапа.
Как и `emits` перехода `StartProcess` в Фазе 3, это только код, возвращаемый
`TransitionResult` — реальная публикация в Outbox/RabbitMQ по-прежнему вне объёма (Event
Publisher — отдельная фаза).

### 2. Guard: `PreviousStageCompleted` (G-S-001)

Дословно (источник: `05_guards_actions_registry.md` §4.2):

> **Что проверяет.** Предыдущий этап имеет финальный статус.
>
> **Логика.**
> ```
> previousStage.status IN [Approved, ApprovedWithComments, Completed]
> ```

Спецификация не проговаривает явно случай «предыдущего этапа не существует» (первый этап
маршрута, `orderIdx = 0`) — это открытый вопрос, закрываемый в этой фазе: **guard проходит
(`true`) для этапа без предыдущего**, поскольку смысл условия — «не активировать этап, пока не
закрыт предшествующий», а для первого этапа предшествующего нет и блокировать нечего. Без
этого допущения `StartProcess` не смог бы активировать вообще ни один процесс.

Реализация:
```
stages = stage.process.stages                       // уже отсортированы по orderIdx (JPA @OrderBy)
previous = stages.filter(s -> s.orderIdx == stage.orderIdx - 1).findFirst()
if previous отсутствует:
    return true                                       // первый этап — блокировать нечего
return previous.status IN ["Approved", "ApprovedWithComments", "Completed"]
```

Строки `"Approved"`/`"ApprovedWithComments"`/`"Completed"` — литералы кода стейт-машины этапа,
не заводятся в этой фазе в `status_registry` (они не являются `toState` ни одного перехода,
реализуемого в этой фазе — `validateTargetStatus` их не проверяет; появятся в `status_registry`,
когда более поздняя фаза реализует переходы, которые реально производят эти статусы).

### 3. Action процесса: `AssignStageTasks` (A-P-001) — активирует первый этап через
   вложенный вызов `TransitionEngine`

Дословно (источник: `05_guards_actions_registry.md` §5.1):

> **Что делает.** Создаёт задачи участникам активного этапа.

Буквально это действие говорит об «активном» этапе, но в контексте `StartProcess` (первый
переход процесса, до этого никакой этап ещё не был активен) оно означает «активировать первый
этап маршрута и создать ему задачи» — то есть выполнить `ActivateStage` для этапа с минимальным
`orderIdx`.

**Важный прецедент, разрешающий это архитектурно.** Спецификация уже описывает и явно
подтверждает (см. `04_state_machines.md`, врезка о `StageRejectedByReturn`, ADR-025,
подтверждено пользователем 2026-09-18) паттерн, когда действие ОДНОЙ стейт-машины программно
инициирует явный переход state machine ДРУГОЙ сущности (`A-RT-002 RejectStagesFrom` посылает
`StageRejectedByReturn` каждому подходящему `StageInstance`). Это тот же принцип: действие
уровня процесса вызывает явный переход уровня этапа через `TransitionEngine`, а не меняет
`stage.status` напрямую — «любое изменение состояния только через явный переход» (ADR-002/
ADR-028) соблюдается.

Реализация `AssignStageTasksAction` (`ru.coordination.approval.service.action`, бин
`assignStageTasksAction`, реализует `Action`):

```
execute(context):
    process = (ProcessInstance) context.entity()
    targetStage = process.stages.minBy(orderIdx)          // ровно один — маршрут линейный
    stageConfig = stateMachineConfigRepository
        .findByEntityTypeAndProcessTypeAndVersion(EntityType.STAGE, process.processType, process.configVersion)
        .orElseThrow(IllegalStateException)                // тот же repository-метод, что и в Фазе 3 (ProcessService)
    stageContext = new TransitionContext(targetStage, context.actorId(), ActorType.SYSTEM, Map.of())
    transitionEngine.transition(
        EntityType.STAGE, targetStage.id, stageConfig.id,
        targetStage.status, TriggerType.SYSTEM_ACTION, stageContext, targetStage::setStatus)
    // результат вложенного перехода не возвращается наружу — TransitionResult перехода
    // StartProcess остаётся результатом перехода ПРОЦЕССА, как и раньше
```

Зависимости бина: `StateMachineConfigRepository`, `TransitionEngine` (обычные
`@RequiredArgsConstructor`-поля — как в `ProcessService`; здесь именно на action-бин, что
приемлемо: реестр резолвит бины через `ComponentResolver`, циклической конструкторской
зависимости с `TransitionEngine` не возникает).

`process_type`/`config_version` предполагаются общими для конфигов `PROCESS` и `STAGE` одной
и той же «версии маршрута» (тот же `Integer configVersion`, что фиксирован на процессе, —
согласовано с принципом Snapshot-on-Start, `04_state_machines.md` §2.6).

Действие вложенного перехода выполняется **в той же транзакции**, что и внешний
`transitionEngine.transition(PROCESS, ...)` (оба метода `@Transactional`, `PROPAGATION.REQUIRED`
по умолчанию — второй вызов присоединяется к уже открытой транзакции первого). Это значит:
если `ActivateStage` не проходит по guard'ам или бросает исключение — весь `StartProcess`
целиком откатывается (см. «Критерии приёмки» и «Открытые вопросы» ниже про поведение при
провале guard'ов этапа).

**Обновление перехода `StartProcess` из Фазы 3** (миграция V18, `state_machine_config`
`entity_type=PROCESS, process_type=STANDARD, version=1`): `transition_config.actions` меняется
с `{SetStartedAt}` на `{AssignStageTasks,SetStartedAt}` (порядок — как в примере §9.1 спеки:
`AssignStageTasks` первым). Выполняется `UPDATE` в миграции этой фазы (не INSERT новой версии
конфига) — на момент этой фазы нет ни одного реального работающего процесса ни в одной среде
(только Testcontainers, поднимаемые с нуля на каждый прогон), поэтому принцип Fork & Drain
(«изменения только additive», `04_state_machines.md` §3.4) здесь не нарушается — версионирование
конфига по-настоящему потребуется, когда появится административный флоу публикации новых
версий (вне объёма всех текущих фаз). Это сознательное упрощение, которое стоит явно
пересмотреть, когда такой флоу появится.

### 4. Action этапа: `AssignParticipantTasks` (A-S-001)

Дословно (источник: `05_guards_actions_registry.md` §5.2):

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

**Упрощение этой фазы**: действие работает с участниками **текущей (последней по
`iterationIdx`) итерации этапа**, которая предполагается уже существующей на момент активации
(создана вручную в тесте через репозитории — так же, как это уже сделано в Фазе 3 для
`AllMandatorySlotsFilledGuard`, который тоже читает «текущую итерацию»). Логика «если у этапа
нет итераций — создать Iter 1» (источник: `04_state_machines.md` §6.3) в этой фазе **не
реализуется** — она осмысленна только когда участники в новую итерацию кладёт
`RouteGeneratorService` (для повторной активации при возврате на этап), которого ещё нет.
Реализация без него создавала бы пустую итерацию без единого участника — бесполезно для
тестирования и не соответствует ни одному реальному сценарию этой фазы. Явно зафиксировать
это в `guard_registry`/`action_registry.description` по аналогии с тем, как это уже сделано
для `AllMandatorySlotsFilled` в миграции V18.

Публикация `approval.task.assigned` (побочный эффект по спеке) — не реализуется (Outbox не
существует, как и в предыдущих фазах); статус участника — единственный наблюдаемый эффект.

Реализация (`ru.coordination.approval.service.action`, бин `assignParticipantTasksAction`):

```
execute(context):
    stage = (StageInstance) context.entity()
    currentIteration = stage.iterations.maxBy(iterationIdx).orElseThrow(IllegalStateException)
    participants = currentIteration.participants
    if stage.executionOrder == SEQUENTIAL:
        first = participants.minBy(orderIdx)
        if first != null: first.status = "Assigned"; first.assignedAt = now
        participants.filter(p -> p != first).forEach(p -> p.status = "Pending")
    else:                                              // PARALLEL или null — по умолчанию
        participants.forEach(p -> { p.status = "Assigned"; p.assignedAt = now })
```

(`participant.status` — `String`, не enum, как и везде в Фазе 1 — литералы `"Assigned"`/
`"Pending"` соответствуют `ParticipantStateMachine`, источник `04_state_machines.md` §7.1.)

### 5. Actions этапа: `SetStageStartedAt` (A-S-002) и `CalcStageDueAt` (A-S-003)

Дословно:
- **A-S-002.** «Фиксирует `stage.startedAt`.» → `stage.setStartedAt(Instant.now())`.
- **A-S-003.** «Вычисляет `stage.dueAt = stage.startedAt + stage.duration`.»

**Открытый вопрос, требующий решения в этой фазе (спека не уточняет единицу `duration`).**
`stage_instance.duration` — `integer`, CHECK `> 0` (Фаза 1, миграция V4), без явно указанной
единицы измерения нигде в документах, доступных для этого тикета. Остальной модуль
последовательно использует **дни** для аналогичных интервалов (автоархивация — «180 дней»,
`A-P-007 ScheduleAutoArchive`, параметр `days: int`). Поэтому в этой фазе `CalcStageDueAt`
реализуется как `stage.setDueAt(stage.getStartedAt().plus(stage.getDuration(),
ChronoUnit.DAYS))`. Если это предположение неверно (например, часы) — исправить и явно
задокументировать в PR; тесты (раздел «Тестирование» ниже) написаны так, чтобы смена единицы
потребовала правки в одном месте.

Реализация (`ru.coordination.approval.service.action`, бин `setStageStartedAtAction` и
`calcStageDueAtAction`, каждое — отдельный `Action`-бин, как и `AssignParticipantTasks` — три
отдельных действия, а не один составной класс, чтобы независимо переиспользоваться в будущих
переходах, например `ActivateTargetStage` при возврате на этап).

## Явно не входит (Out of scope)

- `Decide`/`ParticipantStateMachine` целиком (переходы `AssignTask`, `StartWork`, `Decide`,
  `AutoApprove`, ...), `RecordDecision` (A-PA-001).
- Агрегация решений (`evaluateAggregation`, `AggregationApproved`/`AggregationApprovedWithComments`/
  `AggregationRework`, guards G-S-002–G-S-004) и любые переходы `StageStateMachine`, кроме
  `ActivateStage` (`StageApproved`, `StageOnRework`, `StageAutoApproved`, `CancelStage`,
  `StageResume`, `StageRejectedByReturn`).
- `ActivateNextStage` (A-S-006) — активация ВТОРОГО и последующих этапов маршрута; в этой фазе
  активируется только первый этап (`orderIdx` минимальный), вызванный из `StartProcess`.
- Создание новой `StageIteration` при активации (см. упрощение в разделе 4) — требует
  `RouteGeneratorService`.
- `AssignNextParticipantTask` (A-S-009, только для `executionOrder = Sequential` после решения
  участника — решений в этой фазе ещё нет).
- Отдельный публичный `StageService` как самостоятельный сервис-класс с собственными
  use-case методами — в этой фазе `ActivateStage` вызывается только программно, изнутри
  `AssignStageTasksAction`, никакого прямого пользовательского/API-триггера для него нет
  (см. «Открытые вопросы» — когда появится `StageResume`/пользовательский триггер уровня
  этапа, такой сервис понадобится).
- Публикация `approval.task.assigned`/`approval.stage.activated` в Outbox — Event Publisher,
  отдельная фаза.
- REST API, `UNIFIED` (у него другая таблица переходов этапа, `ActivateStageUnified`, §5.3).

## Что уже есть в репозитории на момент постановки

- `ProcessService.startProcess` (Фаза 3, `src/main/java/ru/coordination/approval/service/
  ProcessService.java`) — не меняется; будет неявно активировать первый этап только за счёт
  того, что миграция этой фазы добавляет `AssignStageTasks` в `actions` перехода
  `StartProcess`.
- `TransitionEngine`/`ModelFactory`/`GuardRegistry`/`ActionRegistry`/`ComponentResolver`
  (Фаза 2, `ru.coordination.approval.engine`) — используются как есть, без изменений.
- `StateMachineConfigRepository.findByEntityTypeAndProcessTypeAndVersion` (Фаза 3,
  `ru.coordination.approval.engine.model`) — переиспользуется для резолвинга STAGE-конфига,
  новый метод не нужен.
- `StageInstance` (`domain/process/StageInstance.java`) — уже имеет все нужные поля:
  `orderIdx`, `duration`, `status` (String), `startedAt`, `dueAt`, `executionOrder`
  (`ExecutionOrder.PARALLEL`/`SEQUENTIAL`), `process` (`@ManyToOne`), `iterations`
  (`@OneToMany`, `@OrderBy("iterationIdx ASC")`).
- `StageIteration`/`Participant` (`domain/process/`) — без изменений; `Participant.status`
  (String), `orderIdx`, `assignedAt`.
- `ExecutionOrder` (`domain/common/ExecutionOrder.java`) — enum `PARALLEL`, `SEQUENTIAL`, уже
  существует.
- `ActorType` (`domain/audit/ActorType.java`) — enum `USER`, `SYSTEM`, `TIMER`, уже
  существует — `SYSTEM` используется для вложенного перехода этапа.
- Guard/action-бины Фазы 3 (`service.guard`, `service.action`) — не меняются, новые бины этой
  фазы добавляются рядом, в те же пакеты.

## Технические требования

- Новые классы в уже существующих пакетах `ru.coordination.approval.service.guard` /
  `ru.coordination.approval.service.action` (соглашение из Фазы 3, `doc/tasks/
  PHASE-03-tasks.md` T3) — не заводить новый top-level пакет ради одной фазы.
- `PreviousStageCompletedGuard` (`service.guard`, бин `previousStageCompletedGuard`) —
  реализует `Guard`, приводит `context.entity()` к `StageInstance`.
- `AssignParticipantTasksAction`, `SetStageStartedAtAction`, `CalcStageDueAtAction`
  (`service.action`, бины `assignParticipantTasksAction`/`setStageStartedAtAction`/
  `calcStageDueAtAction`) — реализуют `Action`, приводят `context.entity()` к `StageInstance`.
- `AssignStageTasksAction` (`service.action`, бин `assignStageTasksAction`) — реализует
  `Action`, приводит `context.entity()` к `ProcessInstance`; внедряет
  `StateMachineConfigRepository` и `TransitionEngine`.
- Новая миграция `V19__seed_activate_stage_transition.sql`:
  - `guard_registry`: `PreviousStageCompleted` → `previousStageCompletedGuard`.
  - `action_registry`: `AssignParticipantTasks` → `assignParticipantTasksAction`,
    `SetStageStartedAt` → `setStageStartedAtAction`, `CalcStageDueAt` → `calcStageDueAtAction`,
    `AssignStageTasks` → `assignStageTasksAction`.
  - `status_registry`: `('Pending', 'STAGE', ...)`, `('Active', 'STAGE', ...)`.
  - `state_machine_config` + `state_config` (Pending, Active) + `transition_config`
    (`ActivateStage`, `Pending → Active`, `SYSTEM_ACTION`, guards
    `{PreviousStageCompleted}`, actions
    `{AssignParticipantTasks,SetStageStartedAt,CalcStageDueAt}`, emits
    `{approval.stage.activated}`) для `entity_type='STAGE', process_type='STANDARD', version=1`.
  - `UPDATE transition_config SET actions = '{AssignStageTasks,SetStartedAt}' WHERE code =
    'StartProcess'` (переход из V18) — обоснование сознательного упрощения см. раздел 3 выше.
- Никаких изменений в `ru.coordination.approval.engine` (движок Фазы 2) и в
  `ProcessService`/`ProcessRepository` (Фаза 3).

## Критерии приёмки

1. `ProcessService.startProcess(...)` на процессе с одним обязательным этапом (участники есть,
   все guards `StartProcess` проходят) — после выполнения: `process.status == "InProgress"`
   (как и в Фазе 3), **и дополнительно** `stage.status == "Active"`, `stage.startedAt`
   заполнено, `stage.dueAt == stage.startedAt + stage.duration` (в днях — см. раздел 5),
   создан `AuditEvent` с `entityType=STAGE, action="ActivateStage"` **в дополнение** к
   `AuditEvent` для `PROCESS/StartProcess` (итого 2 записи аудита за один вызов).
2. Участники этапа с `executionOrder = Parallel` (или `null`) после активации — все в статусе
   `"Assigned"`, `assignedAt` заполнено у каждого.
3. Участники этапа с `executionOrder = Sequential` после активации — первый по `orderIdx` в
   `"Assigned"` с заполненным `assignedAt`, остальные — в `"Pending"`.
4. `PreviousStageCompletedGuard`, вызванный на этапе без предыдущего (`orderIdx` минимальный
   среди этапов процесса) — возвращает `true` независимо от статусов других этапов.
5. `PreviousStageCompletedGuard`, вызванный на этапе, у которого предыдущий этап существует —
   возвращает `true`, если статус предыдущего `∈ {Approved, ApprovedWithComments, Completed}`,
   иначе `false` (юнит-тест — эта фаза не создаёт сценария, где реальный процесс доходит до
   второго этапа, так что это только прямая проверка guard'а, не интеграционная).
6. Если бы `PreviousStageCompletedGuard` вернул `false` для первого этапа (искусственно, в
   юнит-тесте самого движка/guard'а) — вложенный переход `ActivateStage` не выполняется
   (`TransitionEngine` вернёт `TransitionResult.notPerformed(...)` для STAGE-перехода), но
   поведение `AssignStageTasksAction` в этом случае — **не глотать это молча**: решение
   зафиксировано в «Открытые вопросы» ниже, пункт 1.

## Тестирование

- Юнит-тесты `PreviousStageCompletedGuardTest` — без БД, вручную собранные `ProcessInstance`
  с 1–2 `StageInstance` (через `@SuperBuilder`, как в Фазе 3): случаи «нет предыдущего этапа»,
  «предыдущий Approved», «предыдущий Active» (guard `false`).
- Юнит-тесты `AssignParticipantTasksActionTest` — отдельно `Parallel` (все → Assigned) и
  `Sequential` (первый → Assigned, остальные → Pending), плюс граничный случай «в текущей
  итерации нет участников» (действие не падает, просто ничего не делает).
- Юнит-тест на `CalcStageDueAtAction` — проверяет именно арифметику в днях
  (`startedAt.plus(duration, DAYS)`), чтобы при пересмотре единицы (см. открытый вопрос) один
  тест сразу показывал, что менять.
- Обновить/расширить `ProcessServiceIntegrationTest` (Фаза 3, реальные миграции — теперь
  `V1`–`V19`) — добавить проверки критериев приёмки 1–3 к уже существующему сценарию
  `startsProcessWhenAllGuardsPass` (стадия активна, `startedAt`/`dueAt` заполнены, участники
  назначены, 2 `AuditEvent`), не переписывая остальные сценарии Фазы 3 (не-инициатор,
  незаполненные обязательные слоты и т.д. — они не должны измениться в поведении).

## Открытые вопросы

1. Что делать, если вложенный переход `ActivateStage` не выполняется (`guards` не прошли —
   `TransitionResult.notPerformed`) внутри `AssignStageTasksAction`, вызванного как часть
   `StartProcess`? В этой фазе такой случай не должен произойти на практике (первый этап
   всегда проходит `PreviousStageCompleted` по правилу «нет предыдущего — `true`»), но
   `AssignStageTasksAction` — это код, а не guard, поэтому решение принимает Claude Code:
   либо игнорировать `notPerformed` молча (раз он логически недостижим в объёме этой фазы),
   либо бросить `IllegalStateException` как defensive-проверку инварианта. Обосновать выбор в
   PR — то же самое обсуждение, что было по открытому вопросу №1 Фазы 2 (Cowork сверит при
   следующем ревью).
2. Единица измерения `stage.duration` (дни, принято по умолчанию в разделе 5) — подтвердить
   или исправить, если найдётся более прямое указание в коде/данных, которое не было доступно
   при подготовке этого тикета.
3. Когда появится реальный пользовательский/системный триггер уровня этапа отдельно от
   `StartProcess` (например, `StageResume` — userAction, или `StageAutoApproved` — timer),
   потребуется полноценный `StageService` как отдельная точка входа, а не только
   `Action`-бины, вызываемые изнутри действий процесса. Тикет той фазы должен решить, как
   соотносится с уже написанными в этой фазе `AssignParticipantTasksAction`/
   `SetStageStartedAtAction`/`CalcStageDueAtAction` (скорее всего — переиспользовать их как
   есть в составе `actions` новых переходов, ничего не меняя).
4. Как и в Фазе 3 (открытый вопрос №3 того тикета): упрощение «нет создания новой итерации при
   активации» нужно будет пересмотреть в фазе с `RouteGeneratorService` — тикет той фазы
   должен явно сослаться на обе заметки (эту и из Фазы 3).
