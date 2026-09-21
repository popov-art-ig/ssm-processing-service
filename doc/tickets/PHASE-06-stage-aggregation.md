# PHASE-06 — Агрегация решений и закрытие этапа (evaluateAggregation)

> См. `doc/tickets/TEMPLATE.md` за описанием процесса. Тикет самодостаточен: весь материал
> скопирован ниже. Пометки вида «(источник: `04_state_machines.md` §5)» — это атрибуция
> для истории/ревью, не рабочие ссылки — у вас нет доступа к этим файлам.
>
> Подготовлен на основе завершённых фаз 1-5.

## Статус

Не начато

## Контекст

Фаза 5 реализовала принятие решения участником — `participant.status` меняется на `"Decided"`, создаётся запись `decision`. Но **этап остаётся в статусе Active** независимо от того, сколько участников уже приняли решение. Нет механизма агрегации решений и закрытия этапа.

Эта фаза реализует **агрегацию решений** по режимам (`decisionMode`: AND, ANY_APPROVE, ANY_REJECT, ANY_DECISION, FIRST_REJECT_FAIL_FAST) и **закрытие этапа** в соответствующий статус (Approved, ApprovedWithComments, OnRework). Агрегация запускается программно после каждого решения участника.

**Явно НЕ входит:** активация следующего этапа (этап закрыт, процесс остаётся InProgress — PHASE-07), возврат на доработку при OnRework (PHASE-08), автосогласование по срокам (PHASE-16).

## Источники (для истории, не для перехода)

- `04_state_machines.md` §5.2 (переходы StageStateMachine STANDARD)
- `05_guards_actions_registry.md` §4.2 (guards этапа G-S-002 до G-S-005), §5.2 (action A-S-004)
- `02_process_types.md` §3.3 (режимы агрегации STANDARD)
- `business_context_v2.0.md` §6 (режимы агрегации решений)

## Объём фазы (Scope)

### 1. Переходы `StageStateMachine` — закрытие этапа

Из таблицы переходов (источник: `04_state_machines.md` §5.2):

```
| Код                       | From   | To                    | Trigger       | Guards                          |
|---------------------------|--------|-----------------------|---------------|---------------------------------|
| StageApproved             | Active | Approved              | systemAction  | AllParticipantsDecided, AggregationApproved |
| StageApprovedWithComments | Active | ApprovedWithComments  | systemAction  | AllParticipantsDecided, AggregationApprovedWithComments |
| StageOnRework             | Active | OnRework              | systemAction  | AllParticipantsDecided, AggregationRework |
```

**Важно:** Эти переходы имеют `trigger = systemAction` — их вызывает не пользователь напрямую, а программный код (action уровня участника после записи решения).

### 2. Guards агрегации

**G-S-002 `AllParticipantsDecided`** (источник: `05_guards_actions_registry.md` §4.2):

> **Что проверяет.** Все участники этапа приняли решение.
>
> **Логика.**
> ```
> currentIteration = stage.iterations.maxBy(iterationIdx)
> ∀ participant ∈ currentIteration.participants:
>     participant.status IN [Decided, AutoApproved]
> ```

Реализация:
```
execute(context):
    stage = (StageInstance) context.entity()
    currentIteration = stage.iterations с максимальным iterationIdx
    if currentIteration отсутствует: return false
    participants = currentIteration.participants
    if participants пусто: return false  // защита от пустых итераций
    return participants.stream().allMatch(p -> 
        "Decided".equals(p.status) || "AutoApproved".equals(p.status))
```

**G-S-003 `AggregationApproved`** (источник: `05_guards_actions_registry.md` §4.2):

> **Что проверяет.** Агрегация решений даёт результат «Согласован» по режиму `decisionMode`.
>
> **Логика по режимам:**
> - **AND**: все решения == Approve
> - **ANY_APPROVE**: хотя бы одно Approve
> - **ANY_REJECT**: ни одного Reject (все Approve или нет ни одного Reject)
> - **ANY_DECISION**: хотя бы одно решение (любое) — всегда true при AllParticipantsDecided
> - **FIRST_REJECT_FAIL_FAST**: все решения == Approve (иначе этап бы уже был закрыт)

Реализация:
```
execute(context):
    stage = (StageInstance) context.entity()
    currentIteration = stage.iterations с максимальным iterationIdx
    participants = currentIteration.participants
    decisions = participants.map(p -> decisionRepository.findByParticipantId(p.id))
    
    switch (stage.decisionMode) {
        case AND:
            return decisions.stream().allMatch(d -> d.decisionType == APPROVE)
        case ANY_APPROVE:
            return decisions.stream().anyMatch(d -> d.decisionType == APPROVE)
        case ANY_REJECT:
            return decisions.stream().noneMatch(d -> d.decisionType == REJECT)
        case ANY_DECISION:
            return true  // если AllParticipantsDecided прошёл, значит хотя бы одно решение есть
        case FIRST_REJECT_FAIL_FAST:
            return decisions.stream().allMatch(d -> d.decisionType == APPROVE)
        default:
            return false  // неизвестный режим — защита
    }
```

**G-S-004 `AggregationApprovedWithComments`** (источник: `05_guards_actions_registry.md` §4.2):

> **Что проверяет.** Агрегация решений даёт результат «Согласован с замечаниями».
>
> **Логика:** как `AggregationApproved`, но хотя бы одно решение == ApproveWithComments.

Реализация:
```
execute(context):
    // сначала проверяем логику AggregationApproved (переиспользуем или дублируем)
    if (!evaluateLikeAggregationApproved(stage, decisions)) {
        return false
    }
    // плюс хотя бы одно ApproveWithComments
    return decisions.stream().anyMatch(d -> d.decisionType == APPROVE_WITH_COMMENTS)
```

**G-S-005 `AggregationRework`** (источник: `05_guards_actions_registry.md` §4.2):

> **Что проверяет.** Агрегация решений даёт результат «На доработку» (хотя бы один Reject).
>
> **Логика по режимам:**
> - **AND**: хотя бы одно Reject
> - **ANY_APPROVE**: все Reject (ни одного Approve)
> - **ANY_REJECT**: хотя бы одно Reject
> - **ANY_DECISION**: false (любое решение закрывает этап как Approved, не как OnRework)
> - **FIRST_REJECT_FAIL_FAST**: хотя бы одно Reject

Реализация:
```
execute(context):
    stage = (StageInstance) context.entity()
    currentIteration = stage.iterations с максимальным iterationIdx
    participants = currentIteration.participants
    decisions = participants.map(p -> decisionRepository.findByParticipantId(p.id))
    
    switch (stage.decisionMode) {
        case AND:
            return decisions.stream().anyMatch(d -> d.decisionType == REJECT)
        case ANY_APPROVE:
            return decisions.stream().allMatch(d -> d.decisionType == REJECT)
        case ANY_REJECT:
            return decisions.stream().anyMatch(d -> d.decisionType == REJECT)
        case ANY_DECISION:
            return false  // ANY_DECISION всегда Approved, не OnRework
        case FIRST_REJECT_FAIL_FAST:
            return decisions.stream().anyMatch(d -> d.decisionType == REJECT)
        default:
            return false
    }
```

### 3. Action: `EvaluateAggregation` (A-S-004)

**A-S-004 `EvaluateAggregation`** (источник: `05_guards_actions_registry.md` §5.2):

> **Что делает.** Проверяет все решения участников и программно запускает один из трёх переходов этапа (StageApproved/StageApprovedWithComments/StageOnRework).
>
> **Логика.**
> ```
> if not AllParticipantsDecided: return  // ещё не все решили
> 
> if AggregationApprovedWithComments:
>     trigger StageApprovedWithComments
> else if AggregationApproved:
>     trigger StageApproved
> else if AggregationRework:
>     trigger StageOnRework
> ```

Реализация `EvaluateAggregationAction` (`service.action`, бин `evaluateAggregationAction`):

```java
@Component("evaluateAggregationAction")
public class EvaluateAggregationAction implements Action {
    
    private final StateMachineConfigRepository configRepository;
    private final TransitionEngine transitionEngine;
    
    @Override
    public void execute(TransitionContext context) {
        StageInstance stage = (StageInstance) context.entity();
        
        // Резолвить STAGE config
        StateMachineConfig stageConfig = configRepository
            .findByEntityTypeAndProcessTypeAndVersion(
                EntityType.STAGE, 
                stage.getProcess().getProcessType(), 
                stage.getProcess().getConfigVersion()
            )
            .orElseThrow(() -> new IllegalStateException("Stage config not found"));
        
        // Создать контекст для перехода этапа (SYSTEM trigger)
        TransitionContext stageContext = new TransitionContext(
            stage, 
            context.actorId(),  // пробрасываем актёра (инициатора действия — последнего решившего участника)
            ActorType.SYSTEM,   // но триггер — системный
            Map.of()
        );
        
        // Попытаться выполнить переход — TransitionEngine сам проверит guards и выберет первый подходящий
        TransitionResult result = transitionEngine.transition(
            EntityType.STAGE,
            stage.getId(),
            stageConfig.getId(),
            stage.getStatus(),
            TriggerType.SYSTEM_ACTION,
            stageContext,
            stage::setStatus
        );
        
        // Если ни один переход не выполнен (например, AllParticipantsDecided ещё false) — молча выйти
        // Это не ошибка — агрегация вызывается после каждого решения, но срабатывает только когда все решили
        if (!result.performed()) {
            // no-op: ещё не все решили, или неизвестная ситуация
        }
    }
}
```

**Важное уточнение:** `EvaluateAggregation` — это action уровня **участника**, который вызывается после перехода `Decide`. Поэтому его нужно добавить в `actions` перехода `Decide` (миграция V20 из PHASE-05) **или** создать новый переход `ParticipantStateMachine` с этим action. 

**Решение для PHASE-06:** обновить миграцию V20 (переход `Decide`) in-place, добавив `EvaluateAggregation` в конец списка `actions`:

```sql
-- Было: actions = '{RecordDecision,RecordComment}'
-- Станет: actions = '{RecordDecision,RecordComment,EvaluateAggregation}'
UPDATE transition_config 
SET actions = '{RecordDecision,RecordComment,EvaluateAggregation}'
WHERE code = 'Decide' AND config_id = (
    SELECT id FROM state_machine_config 
    WHERE entity_type = 'PARTICIPANT' AND process_type = 'STANDARD' AND version = 1
);
```

(Это допустимое упрощение на этапе разработки — когда появятся реальные процессы в проде, изменения конфигов будут только additive через новые версии; принцип Fork & Drain.)

### 4. Режимы агрегации — справка

(источник: `business_context_v2.0.md` §6, `02_process_types.md` §3.3)

| Режим | Английское название | Логика |
|-------|---------------------|--------|
| **AND** | «И» | Ждём всех; согласован, если все Approve; на доработку, если хотя бы один Reject |
| **ANY_APPROVE** | «Первый согласовавший» | Первый Approve закрывает этап как Approved; если все Reject — OnRework |
| **ANY_REJECT** | «Первый отклонивший» | Первый Reject закрывает этап как OnRework; если все Approve — Approved |
| **ANY_DECISION** | «Первое решение» | Любое первое решение закрывает этап как Approved (Reject тоже считается за Approved — редкий кейс) |
| **FIRST_REJECT_FAIL_FAST** | «Первый отказ прерывает» | Ждём всех, но первый Reject немедленно закрывает этап как OnRework (не ждём остальных) |

**Упрощение в PHASE-06:** режимы **ANY_APPROVE**, **ANY_REJECT**, **ANY_DECISION**, **FIRST_REJECT_FAIL_FAST** логически позволяют закрыть этап **до того, как все участники приняли решение** (например, первый Reject при ANY_REJECT). Но реализация немедленного закрытия (без ожидания остальных) требует либо:
- вызывать `EvaluateAggregation` после каждого решения и проверять специальные guards (например, `FirstRejectReceived`),
- либо усложнять логику guards `AllParticipantsDecided` (делать его условным по режиму).

**В этой фазе упрощаем:** guard `AllParticipantsDecided` всегда ждёт **всех** участников (независимо от режима), агрегация срабатывает только когда все решили. Логика guards `AggregationApproved`/`AggregationRework` реализует правильный результат агрегации, но этап закрывается только когда все участники в статусе Decided/AutoApproved. Оптимизация "закрыть раньше" — в будущей фазе (можно завести отдельный пункт в PHASE-16 или PHASE-28).

## Явно не входит (Out of scope)

- Активация следующего этапа (`ActivateNextStage`) — PHASE-07
- Завершение процесса при закрытии последнего этапа — PHASE-07
- Возврат на доработку (переход процесса `ProcessRework`, InProgress → OnRework) — PHASE-08
- Возобновление после OnRework (`ResumeProcess`) — PHASE-08
- Автосогласование по срокам (AutoApproveParticipant/AutoApproveStage) — PHASE-16
- Немедленное закрытие этапа при режимах ANY_APPROVE/ANY_REJECT/FIRST_REJECT_FAIL_FAST (без ожидания всех участников) — будущая оптимизация
- Переходы `StageStateMachine` для UNIFIED (там другая логика — этапы не агрегируются, все решения фиксируются, закрытие через Completed) — PHASE-13

## Что уже есть в репозитории на момент постановки

- `DecisionService.decide()` (Фаза 5) — записывает решение, меняет статус участника на Decided, вызывает `TransitionEngine` для перехода `Decide`
- `Decision`, `DecisionRepository` (Фаза 5) — таблица `decision` используется, можно читать решения
- `StageInstance.decisionMode` (Фаза 1) — поле уже есть, enum `DecisionMode` существует: `AND`, `ANY_APPROVE`, `ANY_REJECT`, `ANY_DECISION`, `FIRST_REJECT_FAIL_FAST`
- `TransitionEngine` (Фаза 2) — вложенные вызовы для переходов другой сущности уже реализованы (пример: `AssignStageTasksAction` в Фазе 4 вызывает `ActivateStage`)
- `StateMachineConfigRepository.findByEntityTypeAndProcessTypeAndVersion` (Фаза 3) — переиспользуется

## Технические требования

1. Guard-бины (`service.guard`):
   - `AllParticipantsDecidedGuard` (бин `allParticipantsDecidedGuard`)
   - `AggregationApprovedGuard` (бин `aggregationApprovedGuard`) — внедряет `DecisionRepository`
   - `AggregationApprovedWithCommentsGuard` (бин `aggregationApprovedWithCommentsGuard`) — внедряет `DecisionRepository`
   - `AggregationReworkGuard` (бин `aggregationReworkGuard`) — внедряет `DecisionRepository`
   
   Все приводят `context.entity()` к `StageInstance`.

2. Action-бин:
   - `EvaluateAggregationAction` (`service.action`, бин `evaluateAggregationAction`) — внедряет `StateMachineConfigRepository`, `TransitionEngine`; приводит `context.entity()` к `StageInstance` (вызывается как action перехода `Decide` уровня участника, но работает с этапом через навигацию).

3. Новая миграция `V21__seed_stage_aggregation_transitions.sql`:
   - `guard_registry`: `AllParticipantsDecided`, `AggregationApproved`, `AggregationApprovedWithComments`, `AggregationRework`
   - `action_registry`: `EvaluateAggregation`
   - `status_registry`: `('Approved', 'STAGE', ...)`, `('ApprovedWithComments', 'STAGE', ...)`, `('OnRework', 'STAGE', ...)`
   - `transition_config`: три новых перехода для `state_machine_config` (STAGE, STANDARD, version=1):
     - `StageApproved`: `Active → Approved`, `SYSTEM_ACTION`, guards `{AllParticipantsDecided,AggregationApproved}`, actions `{}`, emits `{approval.stage.completed}`
     - `StageApprovedWithComments`: `Active → ApprovedWithComments`, `SYSTEM_ACTION`, guards `{AllParticipantsDecided,AggregationApprovedWithComments}`, actions `{}`, emits `{approval.stage.completed}`
     - `StageOnRework`: `Active → OnRework`, `SYSTEM_ACTION`, guards `{AllParticipantsDecided,AggregationRework}`, actions `{}`, emits `{approval.stage.rejected}`
   - `UPDATE transition_config SET actions = '{RecordDecision,RecordComment,EvaluateAggregation}' WHERE code = 'Decide'` — добавление `EvaluateAggregation` в переход `Decide` из V20.

4. `EvaluateAggregationAction` читает этап через `participant.getStageIteration().getStage()` (навигация по связям JPA) — `context.entity()` для action перехода `Decide` — это `Participant`, но action должен работать с `StageInstance`. Поэтому:
   ```java
   Participant participant = (Participant) context.entity();
   StageInstance stage = participant.getStageIteration().getStage();
   ```

## Критерии приёмки

1. **Режим AND, все Approve:** этап с `decisionMode = AND` и тремя участниками — все три приняли решение `APPROVE` → этап переходит в статус `"Approved"`, создан `AuditEvent` для `StageApproved`.

2. **Режим AND, один Reject:** тот же этап — два `APPROVE`, один `REJECT` → этап переходит в статус `"OnRework"`, создан `AuditEvent` для `StageOnRework`.

3. **Режим AND, один ApproveWithComments:** три участника — два `APPROVE`, один `APPROVE_WITH_COMMENTS` → этап переходит в статус `"ApprovedWithComments"`, создан `AuditEvent` для `StageApprovedWithComments`.

4. **Агрегация не срабатывает до последнего решения:** этап с двумя участниками — первый принял решение → этап остаётся `"Active"`, второй принял решение → этап закрылся (guard `AllParticipantsDecided` блокирует до тех пор).

5. **Режим ANY_APPROVE:** два участника — оба `REJECT` → этап `"OnRework"`; один `APPROVE`, один `REJECT` → этап `"Approved"` (логика ANY_APPROVE: хотя бы один Approve).

6. **Режим ANY_REJECT:** два участника — оба `APPROVE` → этап `"Approved"`; один `APPROVE`, один `REJECT` → этап `"OnRework"` (логика ANY_REJECT: хотя бы один Reject).

7. **Режим FIRST_REJECT_FAIL_FAST:** два участника — оба `APPROVE` → этап `"Approved"`; один `REJECT` → этап `"OnRework"` (ждём обоих, но если хотя бы один Reject — OnRework).

8. **Процесс остаётся InProgress:** после закрытия этапа `process.status` всё ещё `"InProgress"` (не меняется на Approved — это PHASE-07).

## Тестирование

- Юнит-тесты guard-бинов:
  - `AllParticipantsDecidedGuardTest` — без БД, вручную собранный `StageInstance` с итерацией и участниками: все Decided/один Assigned/пустая итерация.
  - `AggregationApprovedGuardTest`, `AggregationApprovedWithCommentsGuardTest`, `AggregationReworkGuardTest` — с mock-`DecisionRepository`, проверка логики по каждому режиму (`DecisionMode`) отдельно.

- Интеграционный тест `StageAggregationIntegrationTest` (Testcontainers, реальные миграции `V1`–`V21`):
  - Переиспользовать фикстуру из `DecisionServiceIntegrationTest` (Фаза 5) — процесс с активным этапом и N участниками.
  - Для каждого режима агрегации (AND, ANY_APPROVE, ANY_REJECT, FIRST_REJECT_FAIL_FAST) создать отдельный тест:
    - Все участники принимают решения через `DecisionService.decide()`.
    - После последнего решения проверить `stage.getStatus()` — должен быть Approved/ApprovedWithComments/OnRework в зависимости от комбинации решений.
    - Проверить, что `process.getStatus()` всё ещё `"InProgress"` (критерий 8).
  - Критерии приёмки 1–8 покрыты через параметризованные тесты (JUnit `@ParameterizedTest`).

## Открытые вопросы

1. **Оптимизация немедленного закрытия этапа** при режимах ANY_APPROVE/ANY_REJECT/FIRST_REJECT_FAIL_FAST (не ждать всех участников) — в этой фазе упрощено (всегда ждём всех). Реализовать как отдельную фазу-оптимизацию (например, PHASE-28) или встроить в PHASE-16 (автосогласование)?

2. **Поведение при `decisionMode = null`** (не задан в шаблоне) — спецификация не уточняет. В этой фазе трактуем как AND (дефолт). Если это неверно — исправить и задокументировать.

3. **`EvaluateAggregation` вызывается из action перехода `Decide`**, который работает с `Participant` как `context.entity()`. Action читает этап через навигацию `participant.getStageIteration().getStage()`. Альтернатива: сделать `EvaluateAggregation` отдельным переходом `ParticipantStateMachine` (например, `TriggerAggregation`), вызываемым после `Decide`. Решение принимает Claude Code — текущий вариант (action с навигацией) проще и соответствует примеру `AssignStageTasksAction` из Фазы 4.

## Ревью Cowork

*(Заполняется после реализации и проверки)*
