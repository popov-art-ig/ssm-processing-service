# PHASE-08 — Возврат на доработку и возобновление процесса (ResumeProcess)

> См. `doc/tickets/TEMPLATE.md` за описанием процесса. Тикет самодостаточен: весь материал
> скопирован ниже. Пометки вида «(источник: `04_state_machines.md` §3)» — это атрибуция
> для истории/ревью, не рабочие ссылки — у вас нет доступа к этим файлам.
>
> Подготовлен на основе завершённых фаз 1-7.

## Статус

Не начато

## Контекст

Фаза 6 реализовала закрытие этапа в статус OnRework, когда агрегация решений даёт результат «на доработку». Но **процесс остаётся в статусе InProgress**, и нет механизма возобновления — инициатор не может выбрать целевой этап и запустить новую итерацию.

Эта фаза реализует:
1. **Переход процесса в статус OnRework** при появлении этапа OnRework.
2. **Возобновление процесса** (`ResumeProcess`) — инициатор выбирает целевой этап из `allowedReturnStages`, создаётся новая итерация, этап активируется.
3. **Создание новых итераций** для целевого этапа и всех последующих (клонирование участников, сброс статусов).

**Явно НЕ входит:** полный lifecycle замечаний (в этой фазе достаточно минимальной проверки — все ли обработаны), отзыв процесса (PHASE-09), ревизии документа (PHASE-26).

## Источники (для истории, не для перехода)

- `04_state_machines.md` §3.2 (переходы ProcessStateMachine STANDARD: ProcessRework, ResumeProcess)
- `05_guards_actions_registry.md` §4.1 (guards процесса G-P-008, G-P-009), §5.3 (actions возврата A-RT-002, A-RT-005)
- `02_process_types.md` §3.5, §3.6, §3.7 (поведение при отклонении, выбор целевого этапа, логика итераций)
- `business_context_v2.0.md` §13 (возврат на доработку STANDARD)

## Объём фазы (Scope)

### 1. Переход `ProcessRework` — процесс идёт на доработку

Из таблицы переходов (источник: `04_state_machines.md` §3.2):

```
| Код           | From       | To       | Trigger      | Guards              | Actions    |
|---------------|------------|----------|--------------|---------------------|------------|
| ProcessRework | InProgress | OnRework | systemAction | HasStageOnRework    | NotifyUser |
```

**Guard G-P-007 `HasStageOnRework`** (источник: `05_guards_actions_registry.md` §4.1):

> **Что проверяет.** Хотя бы один этап процесса в статусе OnRework.
>
> **Логика.**
> ```
> ∃ stage ∈ process.stages: stage.status == "OnRework"
> ```

Реализация `HasStageOnReworkGuard` (`service.guard`, бин `hasStageOnReworkGuard`):

```java
@Component("hasStageOnReworkGuard")
public class HasStageOnReworkGuard implements Guard {
    
    @Override
    public boolean execute(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();
        
        return process.getStages().stream()
            .anyMatch(stage -> "OnRework".equals(stage.getStatus()));
    }
}
```

**Action `NotifyUser`** (A-P-009) — публикация события `approval.process.on_rework` для уведомления инициатора. В этой фазе — заглушка (событие просто добавляется в `emits`, реальная публикация — PHASE-19).

**Триггер:** Переход `ProcessRework` вызывается из action `EvaluateProcessRework` (аналог `EvaluateProcessCompletion` из Фазы 7), который добавляется в конец `actions` перехода `StageOnRework` (обновление миграции V21 из Фазы 6).

Реализация `EvaluateProcessReworkAction` (`service.action`, бин `evaluateProcessReworkAction`):

```java
@Component("evaluateProcessReworkAction")
public class EvaluateProcessReworkAction implements Action {
    
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
        
        // Создать контекст для перехода процесса
        TransitionContext processContext = new TransitionContext(
            process, 
            context.actorId(),
            ActorType.SYSTEM,
            Map.of()
        );
        
        // Выполнить переход ProcessRework (InProgress → OnRework)
        TransitionResult result = transitionEngine.transition(
            EntityType.PROCESS,
            process.getId(),
            processConfig.getId(),
            process.getStatus(),
            TriggerType.SYSTEM_ACTION,
            processContext,
            process::setStatus
        );
        
        if (!result.performed()) {
            // no-op: возможно, процесс уже OnRework
        }
    }
}
```

### 2. Переход `ResumeProcess` — возобновление после доработки

Из таблицы переходов (источник: `04_state_machines.md` §3.2):

```
| Код           | From     | To         | Trigger     | Guards                             | Actions           |
|---------------|----------|------------|-------------|------------------------------------|-------------------|
| ResumeProcess | OnRework | InProgress | userAction  | IsInitiator, AllRemarksProcessed, IsTargetStageAllowed | ActivateTargetStage, RejectStagesFrom |
```

**Guard G-P-008 `AllRemarksProcessed`** (источник: `05_guards_actions_registry.md` §4.1):

> **Что проверяет.** Все замечания процесса обработаны (статус NOT IN [Active, InProgress]).
>
> **Логика.**
> ```
> ∀ remark ∈ process.remarks:
>     remark.status NOT IN [Active, InProgress]
> ```

**Упрощение в PHASE-08:** Полный lifecycle замечаний реализуется в PHASE-11. В этой фазе создаём guard-бин, который **всегда возвращает true** (заглушка), чтобы не блокировать возобновление. Когда появится PHASE-11, guard обновится на реальную проверку.

Реализация `AllRemarksProcessedGuard` (`service.guard`, бин `allRemarksProcessedGuard`):

```java
@Component("allRemarksProcessedGuard")
public class AllRemarksProcessedGuard implements Guard {
    
    @Override
    public boolean execute(TransitionContext context) {
        // PHASE-08: заглушка — всегда разрешаем возобновление
        // TODO PHASE-11: реализовать проверку таблицы remark
        return true;
    }
}
```

**Guard G-P-009 `IsTargetStageAllowed`** (источник: `05_guards_actions_registry.md` §4.1):

> **Что проверяет.** Целевой этап (из параметров) входит в список `allowedReturnStages` шаблона этапа.
>
> **Логика.**
> ```
> targetStageOrderIdx = context.parameters["targetStageOrderIdx"]
> targetStage = findStageByOrderIdx(targetStageOrderIdx)
> currentStageOrderIdx = findStageByStatus("OnRework").orderIdx
> 
> return targetStageOrderIdx <= currentStageOrderIdx
>        AND targetStageOrderIdx IN allowedReturnStages(currentStage)
> ```

**Упрощение:** Поле `allowedReturnStages` хранится в таблице `stage_template` (JSON-массив или отдельная связанная таблица). В этой фазе упрощаем: **разрешаем возврат на любой этап с `orderIdx <= текущего`** (не проверяем `allowedReturnStages`). Полная проверка — когда появятся реальные шаблоны (PHASE-14).

Реализация `IsTargetStageAllowedGuard` (`service.guard`, бин `isTargetStageAllowedGuard`):

```java
@Component("isTargetStageAllowedGuard")
public class IsTargetStageAllowedGuard implements Guard {
    
    @Override
    public boolean execute(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();
        Integer targetStageOrderIdx = (Integer) context.parameters().get("targetStageOrderIdx");
        
        if (targetStageOrderIdx == null) {
            return false;  // обязательный параметр
        }
        
        // Найти этап OnRework (тот, который отклонён)
        StageInstance onReworkStage = process.getStages().stream()
            .filter(s -> "OnRework".equals(s.getStatus()))
            .findFirst()
            .orElse(null);
        
        if (onReworkStage == null) {
            return false;  // нет этапа OnRework — нельзя возобновлять
        }
        
        // PHASE-08: упрощение — разрешаем возврат на любой этап <= текущего
        // TODO PHASE-14: проверить allowedReturnStages из шаблона
        return targetStageOrderIdx >= 1 && targetStageOrderIdx <= onReworkStage.getOrderIdx();
    }
}
```

### 3. Actions возврата на этап

**A-RT-002 `RejectStagesFrom`** (источник: `05_guards_actions_registry.md` §5.3):

> **Что делает.** Присваивает статус `Rejected` этапам между целевым (не включая) и текущим (включая).
>
> **Логика.**
> ```
> targetStageOrderIdx = context.parameters["targetStageOrderIdx"]
> currentStageOrderIdx = findStageByStatus("OnRework").orderIdx
> 
> for orderIdx from (targetStageOrderIdx + 1) to currentStageOrderIdx:
>     stage = findStageByOrderIdx(orderIdx)
>     stage.status = "Rejected"
> ```

Реализация `RejectStagesFromAction` (`service.action`, бин `rejectStagesFromAction`):

```java
@Component("rejectStagesFromAction")
public class RejectStagesFromAction implements Action {
    
    @Override
    public void execute(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();
        Integer targetStageOrderIdx = (Integer) context.parameters().get("targetStageOrderIdx");
        
        // Найти этап OnRework
        StageInstance onReworkStage = process.getStages().stream()
            .filter(s -> "OnRework".equals(s.getStatus()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No OnRework stage found"));
        
        // Отклонить все этапы между целевым и текущим
        process.getStages().stream()
            .filter(s -> s.getOrderIdx() > targetStageOrderIdx 
                      && s.getOrderIdx() <= onReworkStage.getOrderIdx())
            .forEach(stage -> stage.setStatus("Rejected"));
    }
}
```

**A-RT-005 `ActivateTargetStage`** (источник: `05_guards_actions_registry.md` §5.3):

> **Что делает.** Создаёт новую итерацию целевого этапа, клонирует участников, активирует этап (переход `ActivateStage`).
>
> **Логика.**
> ```
> targetStage = findStageByOrderIdx(context.parameters["targetStageOrderIdx"])
> 
> // Создать новую итерацию
> maxIterationIdx = max(targetStage.iterations.map(_.iterationIdx))
> newIteration = new StageIteration()
> newIteration.iterationIdx = maxIterationIdx + 1
> newIteration.stage = targetStage
> 
> // Клонировать участников из последней итерации целевого этапа
> lastIteration = targetStage.iterations.find(_.iterationIdx == maxIterationIdx)
> for participant in lastIteration.participants:
>     newParticipant = clone(participant)
>     newParticipant.status = "Pending"
>     newParticipant.iteration = newIteration
>     newIteration.participants.add(newParticipant)
> 
> targetStage.iterations.add(newIteration)
> save(targetStage)
> 
> // Активировать этап (переход Pending → Active, если ещё Pending; иначе OnRework → Active)
> trigger ActivateStage on targetStage
> ```

Реализация `ActivateTargetStageAction` (`service.action`, бин `activateTargetStageAction`):

```java
@Component("activateTargetStageAction")
public class ActivateTargetStageAction implements Action {
    
    private final StateMachineConfigRepository configRepository;
    private final TransitionEngine transitionEngine;
    
    @Override
    public void execute(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();
        Integer targetStageOrderIdx = (Integer) context.parameters().get("targetStageOrderIdx");
        
        // Найти целевой этап
        StageInstance targetStage = process.getStages().stream()
            .filter(s -> s.getOrderIdx().equals(targetStageOrderIdx))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Target stage not found"));
        
        // Найти текущую максимальную итерацию
        Integer maxIterationIdx = targetStage.getIterations().stream()
            .map(StageIteration::getIterationIdx)
            .max(Integer::compareTo)
            .orElse(0);
        
        StageIteration lastIteration = targetStage.getIterations().stream()
            .filter(it -> it.getIterationIdx().equals(maxIterationIdx))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No iterations found"));
        
        // Создать новую итерацию
        StageIteration newIteration = StageIteration.builder()
            .id(UUID.randomUUID())
            .stage(targetStage)
            .iterationIdx(maxIterationIdx + 1)
            .participants(new ArrayList<>())
            .additionalApprovers(new ArrayList<>())
            .build();
        
        // Клонировать участников (только основные поля, без решений)
        for (Participant oldParticipant : lastIteration.getParticipants()) {
            Participant newParticipant = Participant.builder()
                .id(UUID.randomUUID())
                .stageIteration(newIteration)
                .userId(oldParticipant.getUserId())
                .roleId(oldParticipant.getRoleId())
                .slotName(oldParticipant.getSlotName())
                .orderIdx(oldParticipant.getOrderIdx())
                .status("Pending")  // сбросить статус
                .assignedAt(null)
                .dueAt(null)
                .build();
            newIteration.getParticipants().add(newParticipant);
        }
        
        targetStage.getIterations().add(newIteration);
        // Сохранение произойдёт автоматически (каскад)
        
        // Резолвить STAGE config
        StateMachineConfig stageConfig = configRepository
            .findByEntityTypeAndProcessTypeAndVersion(
                EntityType.STAGE, 
                process.getProcessType(), 
                process.getConfigVersion()
            )
            .orElseThrow(() -> new IllegalStateException("Stage config not found"));
        
        // Активировать целевой этап (Pending/OnRework → Active)
        TransitionContext stageContext = new TransitionContext(
            targetStage, 
            context.actorId(),
            ActorType.SYSTEM,
            Map.of()
        );
        
        TransitionResult result = transitionEngine.transition(
            EntityType.STAGE,
            targetStage.getId(),
            stageConfig.getId(),
            targetStage.getStatus(),
            TriggerType.SYSTEM_ACTION,
            stageContext,
            targetStage::setStatus
        );
        
        if (!result.performed()) {
            throw new IllegalStateException(
                "Failed to activate target stage " + targetStage.getId() + 
                " (orderIdx=" + targetStage.getOrderIdx() + ")"
            );
        }
    }
}
```

**Важно:** Клонирование дополнительных согласующих (`additionalApprovers`) из последней итерации — по правилам «кто назначил» (источник: `business_context_v2.0.md` §15). В PHASE-08 упрощаем: **не клонируем** дополнительных согласующих (список пуст в новой итерации). Полная логика — в PHASE-10.

### 4. `ProcessService.resume()` — use-case метод

```java
@Service
public class ProcessService {
    
    private final ProcessRepository processRepository;
    private final StateMachineConfigRepository configRepository;
    private final TransitionEngine transitionEngine;
    
    @Transactional
    public TransitionResult resume(
        UUID processId, 
        Integer targetStageOrderIdx, 
        UUID actorId
    ) {
        // 1. Загрузить ProcessInstance
        ProcessInstance process = processRepository.findById(processId)
            .orElseThrow(() -> new IllegalArgumentException("Process not found"));
        
        // 2. Валидация: процесс в статусе OnRework
        if (!"OnRework".equals(process.getStatus())) {
            throw new IllegalStateException("Process is not OnRework");
        }
        
        // 3. Резолвить PROCESS config
        StateMachineConfig processConfig = configRepository
            .findByEntityTypeAndProcessTypeAndVersion(
                EntityType.PROCESS, 
                process.getProcessType(), 
                process.getConfigVersion()
            )
            .orElseThrow(() -> new IllegalStateException("Process config not found"));
        
        // 4. Собрать TransitionContext с targetStageOrderIdx
        Map<String, Object> parameters = Map.of("targetStageOrderIdx", targetStageOrderIdx);
        TransitionContext context = new TransitionContext(
            process, actorId, ActorType.USER, parameters
        );
        
        // 5. Вызвать TransitionEngine (ResumeProcess)
        return transitionEngine.transition(
            EntityType.PROCESS,
            process.getId(),
            processConfig.getId(),
            process.getStatus(),
            TriggerType.USER_ACTION,
            context,
            process::setStatus
        );
    }
}
```

### 5. Логика создания итераций (справка)

(источник: `02_process_types.md` §3.7)

> **При активации любого этапа:** если у этапа уже есть итерации — создать новую. Иначе — Iter 1.

**Поведение в PHASE-08:**

- Целевой этап уже имеет итерации (минимум одну, созданную при первой активации в PHASE-04).
- `ActivateTargetStageAction` создаёт новую итерацию с `iterationIdx = max + 1`.
- Этапы после целевого (между целевым и текущим OnRework) получают статус `Rejected` — их итерации НЕ трогаются (остаются как есть).
- Когда эти этапы дойдут до активации в будущем (после прохождения целевого и последующих), они тоже создадут новые итерации (логика та же — «если есть итерации, создать новую»).

## Явно не входит (Out of scope)

- Полный lifecycle замечаний (`RemarkStateMachine`, таблица `remark`) — PHASE-11; в этой фазе guard `AllRemarksProcessed` — заглушка
- Клонирование дополнительных согласующих при создании итерации — PHASE-10
- Проверка `allowedReturnStages` из шаблона — PHASE-14 (в этой фазе разрешаем возврат на любой этап <= текущего)
- Отзыв процесса (`RecallProcess`) — PHASE-09
- Ревизии документа (`processIterationEnabled`, создание нового процесса) — PHASE-26
- REST API `POST /processes/{id}/resume` — PHASE-21
- Переходы возврата для UNIFIED (у UNIFIED нет возврата на доработку) — не применимо

## Что уже есть в репозитории на момент постановки

- `ProcessService` (Фаза 3) — расширяется методом `resume()`
- `StageIteration`, `Participant` (Фаза 1) — поля `iterationIdx`, каскадное сохранение работают
- Guard `IsInitiator` (Фаза 3) — переиспользуется
- `TransitionEngine` (Фаза 2) — вложенные вызовы работают (пример: `ActivateTargetStageAction` → `ActivateStage`)
- Переход `ActivateStage` (Фаза 4) — переиспользуется через вызов TransitionEngine

## Технические требования

1. Guard-бины (`service.guard`):
   - `HasStageOnReworkGuard` (бин `hasStageOnReworkGuard`)
   - `AllRemarksProcessedGuard` (бин `allRemarksProcessedGuard`) — заглушка, всегда `true`
   - `IsTargetStageAllowedGuard` (бин `isTargetStageAllowedGuard`) — упрощённая проверка

2. Action-бины (`service.action`):
   - `EvaluateProcessReworkAction` (бин `evaluateProcessReworkAction`) — внедряет `StateMachineConfigRepository`, `TransitionEngine`
   - `RejectStagesFromAction` (бин `rejectStagesFromAction`) — без зависимостей, меняет статусы этапов
   - `ActivateTargetStageAction` (бин `activateTargetStageAction`) — внедряет `StateMachineConfigRepository`, `TransitionEngine`

3. Новая миграция `V23__seed_process_rework_resume_transitions.sql`:
   - `guard_registry`: `HasStageOnRework`, `AllRemarksProcessed`, `IsTargetStageAllowed`
   - `action_registry`: `EvaluateProcessRework`, `RejectStagesFrom`, `ActivateTargetStage`, `NotifyUser` (заглушка)
   - `status_registry`: `('OnRework', 'PROCESS', ...)`, `('Rejected', 'STAGE', ...)`
   - `transition_config` для PROCESS (STANDARD, version=1):
     - `ProcessRework`: `InProgress → OnRework`, `SYSTEM_ACTION`, guards `{HasStageOnRework}`, actions `{NotifyUser}`, emits `{approval.process.on_rework}`
     - `ResumeProcess`: `OnRework → InProgress`, `USER_ACTION`, guards `{IsInitiator,AllRemarksProcessed,IsTargetStageAllowed}`, actions `{RejectStagesFrom,ActivateTargetStage}`, emits `{approval.process.resumed}`
   - `UPDATE transition_config SET actions = '{EvaluateProcessRework}' WHERE code = 'StageOnRework'` — обновление перехода из V21 (добавление триггера ProcessRework)

4. Расширение `ProcessService` (пакет `service`) — метод `resume(processId, targetStageOrderIdx, actorId)`.

5. `ActivateTargetStageAction` клонирует участников с полями: `userId`, `roleId`, `slotName`, `orderIdx`, статус `"Pending"`, остальные поля (`assignedAt`, `dueAt`, навигация к `decision`) — null/пусто.

## Критерии приёмки

1. **Переход процесса в OnRework:** этап закрыт как OnRework (Фаза 6) → процесс автоматически переходит в статус `"OnRework"`, создан `AuditEvent` для `ProcessRework`.

2. **Возобновление на тот же этап:** процесс с одним этапом OnRework — инициатор вызывает `resume(processId, orderIdx=1, actorId)` → этап получает новую итерацию (iterationIdx=2), участники клонированы со статусом Pending, этап активирован (Active), процесс InProgress.

3. **Возобновление на более ранний этап:** процесс с тремя этапами, третий OnRework — инициатор выбирает целевой этап orderIdx=1 → этапы 2 и 3 получают статус `"Rejected"`, этап 1 получает новую итерацию (iterationIdx=2), этап 1 активирован, процесс InProgress.

4. **Сброс статусов участников:** в новой итерации все участники имеют статус `"Pending"` (не Assigned, не Decided), `assignedAt` и `dueAt` — null.

5. **Guard блокирует non-инициатора:** вызов `resume()` с `actorId`, не равным `process.createdBy` → переход не выполняется (`TransitionResult.performed() == false`), guard `IsInitiator` блокирует.

6. **Guard блокирует недопустимый целевой этап:** вызов `resume()` с `targetStageOrderIdx` больше текущего OnRework этапа → переход не выполняется, guard `IsTargetStageAllowed` блокирует.

7. **Guard блокирует при отсутствии OnRework:** процесс в статусе InProgress (нет этапов OnRework) — вызов `resume()` → `IllegalStateException` в `ProcessService.resume()` (валидация до вызова движка).

8. **Клонирование участников:** новая итерация содержит тех же участников (по `userId`, `roleId`, `slotName`), что и последняя итерация целевого этапа, но с новыми `id` и сброшенными статусами.

## Тестирование

- Юнит-тесты guard-бинов:
  - `HasStageOnReworkGuardTest` — вручную собранный `ProcessInstance` с этапами: есть OnRework, нет OnRework, все Approved.
  - `AllRemarksProcessedGuardTest` — всегда `true` (заглушка).
  - `IsTargetStageAllowedGuardTest` — проверка диапазона `1 <= targetOrderIdx <= currentOrderIdx`: допустимый, меньше 1, больше текущего.

- Юнит-тесты action-бинов:
  - `RejectStagesFromActionTest` — вручную собранный процесс с тремя этапами, targetOrderIdx=1, currentOrderIdx=3 → этапы 2 и 3 получают статус Rejected.
  - `ActivateTargetStageActionTest` — с mock-`TransitionEngine`, проверка создания новой итерации и клонирования участников.

- Интеграционный тест `ProcessResumeIntegrationTest` (Testcontainers, реальные миграции `V1`–`V23`):
  - **Полный цикл возврата:** создать процесс с двумя этапами, первый этап согласован, второй этап отклонён (OnRework) → процесс OnRework → инициатор вызывает `resume(orderIdx=2)` → второй этап получает новую итерацию, активирован → участники принимают решения → процесс завершается.
  - **Возврат на первый этап:** второй этап отклонён → `resume(orderIdx=1)` → первый этап Iter 2, второй этап Rejected → первый этап проходят → второй этап получает Iter 2 (когда до него дойдёт очередь через ActivateNextStage).
  - Критерии приёмки 1–8.

## Открытые вопросы

1. **Клонирование дополнительных согласующих** — в этой фазе не реализовано (список пуст в новой итерации). Когда появится PHASE-10, нужно обновить `ActivateTargetStageAction` для клонирования по правилам «кто назначил».

2. **Поле `allowedReturnStages` в таблице `stage_template`** — спецификация упоминает, но структура хранения не уточнена (JSON-массив `orderIdx` или отдельная таблица `stage_template_return_stage`). В этой фазе не используется (упрощённая проверка), решение по структуре принимается в PHASE-14.

3. **Переход `ActivateStage` для этапа OnRework → Active** — в конфигурации из Фазы 4 переход `ActivateStage` идёт из Pending. Нужен ли отдельный переход `ReactivateStage` (OnRework → Active)? Решение: добавить в V23 второй переход `ReactivateStage` с теми же guards/actions, что и `ActivateStage`, или расширить `from_state` в V19 на `{Pending, OnRework}` (если схема позволяет массив). Claude Code выбирает вариант на основе схемы `transition_config`.

## Ревью Cowork

*(Заполняется после реализации и проверки)*
