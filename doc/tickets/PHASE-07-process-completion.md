# PHASE-07 — Активация следующего этапа и завершение процесса

> См. `doc/tickets/TEMPLATE.md` за описанием процесса. Тикет самодостаточен: весь материал
> скопирован ниже. Пометки вида «(источник: `04_state_machines.md` §3)» — это атрибуция
> для истории/ревью, не рабочие ссылки — у вас нет доступа к этим файлам.
>
> Подготовлен на основе завершённых фаз 1-6.

## Статус

Не начато

## Контекст

Фаза 6 реализовала закрытие этапа (Active → Approved/ApprovedWithComments/OnRework) после агрегации решений участников. Но **процесс остаётся в статусе InProgress** даже после закрытия этапа — следующий этап не активируется автоматически, и если этап был последним, процесс не завершается.

Эта фаза реализует:
1. **Активацию следующего этапа** после закрытия текущего (action `ActivateNextStage`).
2. **Завершение процесса** в статус Approved/ApprovedWithComments, когда последний этап согласован.

**Явно НЕ входит:** возврат на доработку (переход процесса InProgress → OnRework при OnRework статусе этапа — PHASE-08), отзыв процесса (PHASE-09), архивация (PHASE-18).

## Источники (для истории, не для перехода)

- `04_state_machines.md` §3.2 (переходы ProcessStateMachine STANDARD)
- `05_guards_actions_registry.md` §4.1 (guards процесса G-P-006), §5.1 (actions процесса A-P-002)
- `05_guards_actions_registry.md` §5.2 (action этапа A-S-006)
- `02_process_types.md` §3.4 (формирование итога STANDARD)

## Объём фазы (Scope)

### 1. Action: `ActivateNextStage` (A-S-006)

**A-S-006 `ActivateNextStage`** (источник: `05_guards_actions_registry.md` §5.2):

> **Что делает.** Находит следующий по `orderIdx` этап и активирует его (запускает переход `ActivateStage`).
>
> **Логика.**
> ```
> currentStage = context.entity()
> nextStage = findNextStage(currentStage.orderIdx + 1)
> if nextStage exists:
>     trigger ActivateStage on nextStage
> else:
>     // это был последний этап — ничего не делаем, завершение процесса произойдёт отдельно
> ```

Реализация `ActivateNextStageAction` (`service.action`, бин `activateNextStageAction`):

```java
@Component("activateNextStageAction")
public class ActivateNextStageAction implements Action {
    
    private final StateMachineConfigRepository configRepository;
    private final TransitionEngine transitionEngine;
    
    @Override
    public void execute(TransitionContext context) {
        StageInstance currentStage = (StageInstance) context.entity();
        ProcessInstance process = currentStage.getProcess();
        
        // Найти следующий этап по orderIdx
        StageInstance nextStage = process.getStages().stream()
            .filter(s -> s.getOrderIdx() == currentStage.getOrderIdx() + 1)
            .findFirst()
            .orElse(null);
        
        if (nextStage == null) {
            // Это был последний этап — завершение процесса произойдёт отдельно (ProcessApproved)
            return;
        }
        
        // Резолвить STAGE config
        StateMachineConfig stageConfig = configRepository
            .findByEntityTypeAndProcessTypeAndVersion(
                EntityType.STAGE, 
                process.getProcessType(), 
                process.getConfigVersion()
            )
            .orElseThrow(() -> new IllegalStateException("Stage config not found"));
        
        // Создать контекст для перехода этапа (SYSTEM trigger)
        TransitionContext nextStageContext = new TransitionContext(
            nextStage, 
            context.actorId(),
            ActorType.SYSTEM,
            Map.of()
        );
        
        // Выполнить переход ActivateStage (Pending → Active)
        TransitionResult result = transitionEngine.transition(
            EntityType.STAGE,
            nextStage.getId(),
            stageConfig.getId(),
            nextStage.getStatus(),
            TriggerType.SYSTEM_ACTION,
            nextStageContext,
            nextStage::setStatus
        );
        
        if (!result.performed()) {
            throw new IllegalStateException(
                "Failed to activate next stage " + nextStage.getId() + 
                " (orderIdx=" + nextStage.getOrderIdx() + ")"
            );
        }
    }
}
```

**Важно:** `ActivateNextStage` добавляется в `actions` переходов `StageApproved` и `StageApprovedWithComments` (миграция V21 из PHASE-06 обновляется in-place или создаётся V22). Переход `StageOnRework` **НЕ** активирует следующий этап (процесс идёт на доработку — PHASE-08).

### 2. Guard: `AllStagesCompleted` (G-P-006)

**G-P-006 `AllStagesCompleted`** (источник: `05_guards_actions_registry.md` §4.1):

> **Что проверяет.** Все этапы процесса завершены (статус IN [Approved, ApprovedWithComments, Completed]).
>
> **Логика.**
> ```
> ∀ stage ∈ process.stages:
>     stage.status IN [Approved, ApprovedWithComments, Completed]
> ```

Реализация `AllStagesCompletedGuard` (`service.guard`, бин `allStagesCompletedGuard`):

```java
@Component("allStagesCompletedGuard")
public class AllStagesCompletedGuard implements Guard {
    
    @Override
    public boolean execute(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();
        
        if (process.getStages().isEmpty()) {
            return false;  // защита от процессов без этапов
        }
        
        return process.getStages().stream().allMatch(stage -> {
            String status = stage.getStatus();
            return "Approved".equals(status) 
                || "ApprovedWithComments".equals(status)
                || "Completed".equals(status);  // для UNIFIED (PHASE-13)
        });
    }
}
```

### 3. Action: `CompleteProcess` (A-P-002)

**A-P-002 `CompleteProcess`** (источник: `05_guards_actions_registry.md` §5.1):

> **Что делает.** Устанавливает `completedAt = now()` на процессе.
>
> **Логика.**
> ```
> process.completedAt = now()
> save(process)
> ```

Реализация `CompleteProcessAction` (`service.action`, бин `completeProcessAction`):

```java
@Component("completeProcessAction")
public class CompleteProcessAction implements Action {
    
    @Override
    public void execute(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();
        process.setCompletedAt(Instant.now());
        // Сохранение произойдёт автоматически (TransitionEngine работает в @Transactional)
    }
}
```

### 4. Переходы `ProcessStateMachine` — завершение процесса

Из таблицы переходов (источник: `04_state_machines.md` §3.2):

```
| Код                       | From       | To                    | Trigger       | Guards              | Actions         |
|---------------------------|------------|-----------------------|---------------|---------------------|-----------------|
| ProcessApproved           | InProgress | Approved              | systemAction  | AllStagesCompleted  | CompleteProcess |
| ProcessApprovedWithComments | InProgress | ApprovedWithComments | systemAction  | AllStagesCompleted, HasComments | CompleteProcess |
```

**Guard `HasComments`** (G-P-007) — проверяет, что хотя бы один этап завершён как ApprovedWithComments:

```java
@Component("hasCommentsGuard")
public class HasCommentsGuard implements Guard {
    
    @Override
    public boolean execute(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();
        
        return process.getStages().stream()
            .anyMatch(stage -> "ApprovedWithComments".equals(stage.getStatus()));
    }
}
```

### 5. Action: `EvaluateProcessCompletion` — триггер завершения процесса

Как и с агрегацией этапа (PHASE-06), завершение процесса нужно **проверять после каждого закрытия этапа**. Для этого создаётся action `EvaluateProcessCompletion` (аналог `EvaluateAggregation`), который вызывается из переходов `StageApproved`/`StageApprovedWithComments`.

**Реализация `EvaluateProcessCompletionAction`** (`service.action`, бин `evaluateProcessCompletionAction`):

```java
@Component("evaluateProcessCompletionAction")
public class EvaluateProcessCompletionAction implements Action {
    
    private final StateMachineConfigRepository configRepository;
    private final TransitionEngine transitionEngine;
    
    @Override
    public void execute(TransitionContext context) {
        StageInstance stage = (StageInstance) context.entity();
        ProcessInstance process = stage.getProcess();
        
        // Резолвить PROCESS config
        StateMachineConfig processConfig = configRepository
            .findByEntityTypeAndProcessTypeAndVersion(
                EntityType.PROCESS, 
                process.getProcessType(), 
                process.getConfigVersion()
            )
            .orElseThrow(() -> new IllegalStateException("Process config not found"));
        
        // Создать контекст для перехода процесса (SYSTEM trigger)
        TransitionContext processContext = new TransitionContext(
            process, 
            context.actorId(),
            ActorType.SYSTEM,
            Map.of()
        );
        
        // Попытаться выполнить переход — TransitionEngine сам проверит guards
        TransitionResult result = transitionEngine.transition(
            EntityType.PROCESS,
            process.getId(),
            processConfig.getId(),
            process.getStatus(),
            TriggerType.SYSTEM_ACTION,
            processContext,
            process::setStatus
        );
        
        // Если не выполнен (не все этапы завершены) — молча выйти
        if (!result.performed()) {
            // no-op: ещё не все этапы завершены
        }
    }
}
```

**Добавление в миграцию:** action `EvaluateProcessCompletion` добавляется в конец `actions` переходов `StageApproved` и `StageApprovedWithComments` (после `ActivateNextStage`).

### 6. Формирование итога процесса

(источник: `02_process_types.md` §3.4)

> **Ключевая идея.** Итог процесса определяется решением **последнего этапа**. До последнего этапа процесс доходит только после успешного согласования всех предыдущих.

**Логика выбора между `ProcessApproved` и `ProcessApprovedWithComments`:**

- Если **хотя бы один** этап завершён как `ApprovedWithComments` → процесс `ApprovedWithComments`.
- Иначе (все этапы `Approved`) → процесс `Approved`.

Guard `HasComments` реализует эту проверку. `TransitionEngine` пробует переходы в порядке конфигурации:
1. Сначала `ProcessApprovedWithComments` (guards: `AllStagesCompleted` + `HasComments`).
2. Если не подошёл — `ProcessApproved` (guards: `AllStagesCompleted`).

Порядок переходов в миграции V22 (или обновлении V21) важен — `ProcessApprovedWithComments` должен быть **раньше** `ProcessApproved` в таблице `transition_config` (по `id` или явно заданному `order_idx`, если такое поле есть; иначе TransitionEngine пробует все подходящие переходы и возвращает первый performed — нужно убедиться, что WithComments проверяется раньше).

**Упрощение:** Если TransitionEngine не гарантирует порядок (проверяет все переходы параллельно), можно объединить в один переход с action, который программно выбирает между Approved и ApprovedWithComments (как `EvaluateAggregation`). Но текущая архитектура (Фаза 2) перебирает переходы в порядке из конфигурации → достаточно правильного порядка в миграции.

## Явно не входит (Out of scope)

- Переход процесса `ProcessRework` (InProgress → OnRework) при статусе этапа OnRework — PHASE-08
- Возобновление процесса (`ResumeProcess`, OnRework → InProgress) — PHASE-08
- Отзыв процесса (`RecallProcess`) — PHASE-09
- Автоархивация (`AutoArchive`, Approved/ApprovedWithComments → Archived через 180 дней) — PHASE-18
- Завершение UNIFIED процесса (там переход в `AwaitingFinalDecision`, а не сразу в Approved) — PHASE-13

## Что уже есть в репозитории на момент постановки

- `StageInstance` с полем `orderIdx` (Фаза 1) — используется для поиска следующего этапа
- `ProcessInstance.completedAt` (Фаза 1) — поле уже есть, заполняется в этой фазе
- `TransitionEngine` (Фаза 2) — вложенные вызовы работают (пример: `AssignStageTasksAction` в Фазе 4, `EvaluateAggregationAction` в Фазе 6)
- Переход `ActivateStage` (Фаза 4) — переиспользуется через вызов TransitionEngine
- Переходы `StageApproved`/`StageApprovedWithComments` (Фаза 6) — обновляются, добавляются actions

## Технические требования

1. Guard-бины (`service.guard`):
   - `AllStagesCompletedGuard` (бин `allStagesCompletedGuard`)
   - `HasCommentsGuard` (бин `hasCommentsGuard`)

2. Action-бины (`service.action`):
   - `ActivateNextStageAction` (бин `activateNextStageAction`) — внедряет `StateMachineConfigRepository`, `TransitionEngine`
   - `CompleteProcessAction` (бин `completeProcessAction`) — не требует зависимостей, только устанавливает `completedAt`
   - `EvaluateProcessCompletionAction` (бин `evaluateProcessCompletionAction`) — внедряет `StateMachineConfigRepository`, `TransitionEngine`

3. Новая миграция `V22__seed_process_completion_transitions.sql`:
   - `guard_registry`: `AllStagesCompleted`, `HasComments`
   - `action_registry`: `ActivateNextStage`, `CompleteProcess`, `EvaluateProcessCompletion`
   - `status_registry`: `('Approved', 'PROCESS', ...)`, `('ApprovedWithComments', 'PROCESS', ...)`
   - `transition_config` для `state_machine_config` (PROCESS, STANDARD, version=1):
     - `ProcessApprovedWithComments`: `InProgress → ApprovedWithComments`, `SYSTEM_ACTION`, guards `{AllStagesCompleted,HasComments}`, actions `{CompleteProcess}`, emits `{approval.process.completed}`
     - `ProcessApproved`: `InProgress → Approved`, `SYSTEM_ACTION`, guards `{AllStagesCompleted}`, actions `{CompleteProcess}`, emits `{approval.process.completed}`
   - `UPDATE transition_config SET actions = '{ActivateNextStage,EvaluateProcessCompletion}' WHERE code = 'StageApproved'` — обновление перехода из V21
   - `UPDATE transition_config SET actions = '{ActivateNextStage,EvaluateProcessCompletion}' WHERE code = 'StageApprovedWithComments'` — обновление перехода из V21
   - **Важно:** `ProcessApprovedWithComments` должен быть вставлен **раньше** `ProcessApproved` (меньший `id` или явный `order_idx`), чтобы TransitionEngine проверял его первым.

4. `ActivateNextStageAction` бросает `IllegalStateException`, если следующий этап не активировался (guard `PreviousStageCompleted` из Фазы 4 должен пропустить — предыдущий этап уже Approved/ApprovedWithComments). Это нарушение инварианта (как и в открытом вопросе №1 PHASE-04).

5. `EvaluateProcessCompletionAction` читает процесс через `stage.getProcess()` (навигация по связям JPA) — `context.entity()` для action перехода этапа — это `StageInstance`.

## Критерии приёмки

1. **Активация следующего этапа:** процесс с двумя этапами — первый этап закрыт как Approved → второй этап автоматически переходит в статус `"Active"` (Pending → Active), участникам второго этапа назначены задачи (Assigned).

2. **Завершение процесса (один этап, Approved):** процесс с одним этапом — этап закрыт как Approved → процесс переходит в статус `"Approved"`, `completedAt` установлен.

3. **Завершение процесса (два этапа, ApprovedWithComments):** процесс с двумя этапами — первый Approved, второй ApprovedWithComments → процесс переходит в статус `"ApprovedWithComments"` (не Approved), `completedAt` установлен.

4. **Промежуточное состояние:** процесс с тремя этапами — первый закрыт как Approved, второй активирован → процесс всё ещё `"InProgress"` (не завершён, пока третий этап не пройдёт).

5. **Несколько этапов Approved:** процесс с тремя этапами — все три Approved → процесс `"Approved"` (не ApprovedWithComments).

6. **Хотя бы один ApprovedWithComments:** процесс с тремя этапами — первый Approved, второй ApprovedWithComments, третий Approved → процесс `"ApprovedWithComments"`.

7. **Событие `approval.process.completed`:** при завершении процесса создано событие с `eventType = 'approval.process.completed'` в `emittedEvents` (проверить через `TransitionResult` или таблицу `audit_event`).

8. **Этап OnRework не активирует следующий:** процесс с двумя этапами — первый закрыт как OnRework → второй этап остаётся `"Pending"`, процесс остаётся `"InProgress"` (завершение не происходит — возврат на доработку реализуется в PHASE-08).

## Тестирование

- Юнит-тесты guard-бинов:
  - `AllStagesCompletedGuardTest` — вручную собранный `ProcessInstance` с этапами в разных статусах: все Approved, один Pending, один OnRework, пустой список этапов.
  - `HasCommentsGuardTest` — процесс с этапами: все Approved, хотя бы один ApprovedWithComments.

- Юнит-тесты action-бинов:
  - `ActivateNextStageActionTest` — с mock-`TransitionEngine`, проверка вызова `transition()` с правильными параметрами для следующего этапа; случай "нет следующего этапа" (последний).
  - `CompleteProcessActionTest` — проверка установки `completedAt` (без mock, просто `process.getCompletedAt() != null`).

- Интеграционный тест `ProcessCompletionIntegrationTest` (Testcontainers, реальные миграции `V1`–`V22`):
  - **Полный цикл процесса (один этап):** создать процесс, запустить (`StartProcess`), все участники принимают решение Approve → процесс автоматически Approved, `completedAt` установлен.
  - **Полный цикл процесса (два этапа):** первый этап согласован → второй этап активирован автоматически → участники второго этапа решают → процесс завершён.
  - **Проверка ApprovedWithComments:** один из этапов ApprovedWithComments → процесс завершается как ApprovedWithComments.
  - **OnRework не завершает процесс:** первый этап OnRework → второй этап не активирован, процесс InProgress (критерий 8).
  - Покрыть критерии приёмки 1–8.

## Открытые вопросы

1. **Порядок переходов в TransitionEngine** — гарантирует ли он порядок проверки переходов по порядку вставки в `transition_config`? Если нет — нужно либо добавить поле `order_idx` в таблицу `transition_config`, либо реализовать `EvaluateProcessCompletion` как action с явным выбором между Approved/ApprovedWithComments (аналогично `EvaluateAggregation`). Решение принимает Claude Code на основе кода TransitionEngine из Фазы 2.

2. **Поведение при `ActivateNextStage` на последнем этапе** — action возвращает молча (no-op), завершение процесса обрабатывается `EvaluateProcessCompletion`. Это правильно? Альтернатива: убрать `ActivateNextStage` из `StageApproved`/`StageApprovedWithComments` и всегда вызывать только `EvaluateProcessCompletion`, который сам решает, активировать следующий этап или завершить процесс. Текущий вариант (два отдельных action) проще и соответствует принципу разделения ответственности.

3. **Guard `AllStagesCompleted` проверяет статус `Completed`** (для UNIFIED, PHASE-13), хотя в STANDARD этого статуса нет. Это упреждающая реализация — допустимо? Да, это не влияет на STANDARD (у STANDARD этапов никогда не будет статуса Completed), но упрощает интеграцию UNIFIED в будущем.

## Ревью Cowork

*(Заполняется после реализации и проверки)*
