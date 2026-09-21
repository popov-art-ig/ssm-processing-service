# PHASE-11 — Замечания и комментарии (Remark, Comment)

> См. `doc/tickets/TEMPLATE.md` за описанием процесса. Тикет самодостаточен: весь материал
> скопирован ниже. Пометки вида «(источник: `04_state_machines.md` §8)» — это атрибуция
> для истории/ревью, не рабочие ссылки — у вас нет доступа к этим файлам.
>
> Подготовлен на основе завершённых фаз 1-8.

## Статус

Не начато

## Контекст

Фаза 5 реализовала запись комментариев при принятии решения участником (таблица `comment`), но **замечания не работают** — таблица `remark` не используется, нет `RemarkStateMachine`, нет lifecycle (создание → обработка → закрытие). Фаза 8 реализовала guard `AllRemarksProcessed`, но он всегда возвращает `true` (заглушка).

Эта фаза реализует:
1. **Полный lifecycle замечаний** через `RemarkStateMachine` (Created → Active → Resolved/Rejected → Closed).
2. **`RemarkService`** для создания и обработки замечаний.
3. **Обновление guard `AllRemarksProcessed`** на реальную проверку статусов замечаний.
4. **Автоматическую обработку замечаний** при закрытии этапа (action `AutoProcessOpenRemarks`).

**Явно НЕ входит:** REST API для замечаний (PHASE-23), файловые вложения, уведомления инициатора (событие `approval.remark.created` будет публиковаться, но реальная доставка — PHASE-19).

## Источники (для истории, не для перехода)

- `04_state_machines.md` §8 (RemarkStateMachine, состояния и переходы)
- `05_guards_actions_registry.md` §4.4 (guards замечаний), §5.5 (actions замечаний)
- `08_db_schema.md` §11 (таблица `remark`)
- `business_context_v2.0.md` §21 (бизнес-правила замечаний)

## Объём фазы (Scope)

### 1. `RemarkStateMachine` — состояния и переходы

Из таблицы переходов (источник: `04_state_machines.md` §8.2):

```
| Код              | From    | To         | Trigger      | Guards              | Actions            |
|------------------|---------|------------|--------------|---------------------|--------------------|
| CreateRemark     | —       | Created    | userAction   | IsParticipant       | NotifyInitiator    |
| ActivateRemark   | Created | Active     | systemAction | —                   | —                  |
| MarkNotRequired  | Active  | NotRequired| userAction   | IsInitiator         | —                  |
| ResolveRemark    | Active  | Resolved   | userAction   | IsInitiator         | —                  |
| RejectRemark     | Resolved| Active     | userAction   | IsRemarkAuthor      | NotifyInitiator    |
| CloseRemark      | Resolved| Closed     | userAction   | IsRemarkAuthor      | —                  |
| AutoProcessRemark| Active  | Closed     | systemAction | StageNotClosed      | —                  |
```

**Важно:** Переход `CreateRemark` имеет `From = —` (отсутствие начального состояния) — это создание новой сущности `Remark` с начальным статусом `Created`. В коде это означает, что `RemarkService.create()` **не вызывает TransitionEngine** для `CreateRemark`, а сразу создаёт запись с `status = "Created"` и затем вызывает переход `ActivateRemark` (Created → Active).

### 2. Guards замечаний

**G-R-001 `IsRemarkAuthor`** (источник: `05_guards_actions_registry.md` §4.4):

> **Что проверяет.** Текущий пользователь — автор замечания.
>
> **Логика.**
> ```
> context.currentUser.id == remark.createdBy
> ```

Реализация `IsRemarkAuthorGuard` (`service.guard`, бин `isRemarkAuthorGuard`):

```java
@Component("isRemarkAuthorGuard")
public class IsRemarkAuthorGuard implements Guard {
    
    @Override
    public boolean execute(TransitionContext context) {
        Remark remark = (Remark) context.entity();
        return context.actorId().equals(remark.getCreatedBy());
    }
}
```

**G-R-002 `StageNotClosed`** (источник: `05_guards_actions_registry.md` §4.4):

> **Что проверяет.** Этап замечания не закрыт (статус NOT IN [Approved, ApprovedWithComments, OnRework, Rejected, Completed]).
>
> **Логика.**
> ```
> stage = remark.stage
> stage.status NOT IN [Approved, ApprovedWithComments, OnRework, Rejected, Completed]
> ```

Реализация `StageNotClosedGuard` (`service.guard`, бин `stageNotClosedGuard`):

```java
@Component("stageNotClosedGuard")
public class StageNotClosedGuard implements Guard {
    
    @Override
    public boolean execute(TransitionContext context) {
        Remark remark = (Remark) context.entity();
        StageInstance stage = remark.getStage();
        
        String status = stage.getStatus();
        return !"Approved".equals(status)
            && !"ApprovedWithComments".equals(status)
            && !"OnRework".equals(status)
            && !"Rejected".equals(status)
            && !"Completed".equals(status);
    }
}
```

**G-PA-005 `IsParticipant`** (источник: `05_guards_actions_registry.md` §4.3) — переиспользуется для `CreateRemark`:

> **Что проверяет.** Текущий пользователь — участник процесса (любой, не обязательно создатель замечания).
>
> **Логика.**
> ```
> process = remark.process
> ∃ stage ∈ process.stages:
>     ∃ iteration ∈ stage.iterations:
>         ∃ participant ∈ iteration.participants:
>             participant.userId == context.currentUser.id
> ```

Реализация `IsParticipantGuard` (`service.guard`, бин `isParticipantGuard`):

```java
@Component("isParticipantGuard")
public class IsParticipantGuard implements Guard {
    
    @Override
    public boolean execute(TransitionContext context) {
        // Для Remark: проверить, что actorId — участник процесса
        // context.entity() может быть Remark (при CreateRemark) или Process (для других кейсов)
        // Упрощение: считаем, что в context.parameters() есть processId
        UUID processId = (UUID) context.parameters().get("processId");
        if (processId == null) {
            return false;
        }
        
        // Загрузить процесс и проверить участие actorId
        // TODO: внедрить ProcessRepository для проверки
        // Пока заглушка — всегда true (упрощение PHASE-11)
        return true;
    }
}
```

**Упрощение PHASE-11:** Guard `IsParticipant` — заглушка (всегда `true`), чтобы не усложнять код внедрением `ProcessRepository` в guard. Полная проверка — в будущей оптимизации (PHASE-28).

### 3. Actions замечаний

**A-R-001 `NotifyInitiator`** (источник: `05_guards_actions_registry.md` §5.5):

> **Что делает.** Публикует событие `approval.remark.created` для уведомления инициатора.
>
> **Логика.**
> ```
> emit event("approval.remark.created", remarkId, processId)
> ```

Реализация `NotifyInitiatorAction` (`service.action`, бин `notifyInitiatorAction`) — заглушка (событие добавляется в `emits` конфигурации, реальная публикация — PHASE-19).

**A-R-002 `AutoProcessOpenRemarks`** (источник: `05_guards_actions_registry.md` §5.5):

> **Что делает.** Закрывает все открытые (Active) замечания этапа при закрытии этапа.
>
> **Логика.**
> ```
> stage = context.entity()
> for remark in stage.remarks where remark.status == "Active":
>     trigger AutoProcessRemark on remark
> ```

Реализация `AutoProcessOpenRemarksAction` (`service.action`, бин `autoProcessOpenRemarksAction`):

```java
@Component("autoProcessOpenRemarksAction")
public class AutoProcessOpenRemarksAction implements Action {
    
    private final RemarkRepository remarkRepository;
    private final StateMachineConfigRepository configRepository;
    private final TransitionEngine transitionEngine;
    
    @Override
    public void execute(TransitionContext context) {
        StageInstance stage = (StageInstance) context.entity();
        
        // Найти все Active замечания этапа
        List<Remark> activeRemarks = remarkRepository.findByStageIdAndStatus(
            stage.getId(), "Active"
        );
        
        if (activeRemarks.isEmpty()) {
            return;  // нет открытых замечаний
        }
        
        // Резолвить REMARK config
        StateMachineConfig remarkConfig = configRepository
            .findByEntityTypeAndProcessTypeAndVersion(
                EntityType.REMARK, 
                stage.getProcess().getProcessType(), 
                stage.getProcess().getConfigVersion()
            )
            .orElseThrow(() -> new IllegalStateException("Remark config not found"));
        
        // Закрыть каждое замечание (AutoProcessRemark: Active → Closed)
        for (Remark remark : activeRemarks) {
            TransitionContext remarkContext = new TransitionContext(
                remark, 
                context.actorId(),
                ActorType.SYSTEM,
                Map.of()
            );
            
            TransitionResult result = transitionEngine.transition(
                EntityType.REMARK,
                remark.getId(),
                remarkConfig.getId(),
                remark.getStatus(),
                TriggerType.SYSTEM_ACTION,
                remarkContext,
                remark::setStatus
            );
            
            if (!result.performed()) {
                // Логирование: не удалось закрыть замечание, но не бросаем исключение
                // (этап всё равно закрывается)
            }
        }
    }
}
```

### 4. `RemarkService` — use-case методы

```java
@Service
public class RemarkService {
    
    private final RemarkRepository remarkRepository;
    private final ProcessRepository processRepository;
    private final StateMachineConfigRepository configRepository;
    private final TransitionEngine transitionEngine;
    
    /**
     * Создать замечание (Created → Active автоматически)
     */
    @Transactional
    public Remark create(
        UUID processId,
        UUID stageId,
        UUID participantId,
        String text,
        UUID actorId
    ) {
        // Валидация: процесс и этап существуют
        ProcessInstance process = processRepository.findById(processId)
            .orElseThrow(() -> new IllegalArgumentException("Process not found"));
        
        StageInstance stage = process.getStages().stream()
            .filter(s -> s.getId().equals(stageId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Stage not found"));
        
        // Создать Remark с начальным статусом Created
        Remark remark = Remark.builder()
            .id(UUID.randomUUID())
            .process(process)
            .stage(stage)
            .participantId(participantId)
            .text(text)
            .status("Created")
            .createdAt(Instant.now())
            .createdBy(actorId)
            .build();
        
        remarkRepository.save(remark);
        
        // Сразу активировать замечание (Created → Active)
        StateMachineConfig remarkConfig = configRepository
            .findByEntityTypeAndProcessTypeAndVersion(
                EntityType.REMARK, 
                process.getProcessType(), 
                process.getConfigVersion()
            )
            .orElseThrow(() -> new IllegalStateException("Remark config not found"));
        
        TransitionContext context = new TransitionContext(
            remark, actorId, ActorType.SYSTEM, Map.of()
        );
        
        TransitionResult result = transitionEngine.transition(
            EntityType.REMARK,
            remark.getId(),
            remarkConfig.getId(),
            remark.getStatus(),
            TriggerType.SYSTEM_ACTION,
            context,
            remark::setStatus
        );
        
        if (!result.performed()) {
            throw new IllegalStateException("Failed to activate remark");
        }
        
        return remark;
    }
    
    /**
     * Исправить замечание (Active → Resolved)
     */
    @Transactional
    public TransitionResult resolve(UUID remarkId, UUID actorId) {
        Remark remark = remarkRepository.findById(remarkId)
            .orElseThrow(() -> new IllegalArgumentException("Remark not found"));
        
        return executeTransition(remark, actorId, TriggerType.USER_ACTION);
    }
    
    /**
     * Отклонить исправление (Resolved → Active)
     */
    @Transactional
    public TransitionResult reject(UUID remarkId, UUID actorId) {
        Remark remark = remarkRepository.findById(remarkId)
            .orElseThrow(() -> new IllegalArgumentException("Remark not found"));
        
        return executeTransition(remark, actorId, TriggerType.USER_ACTION);
    }
    
    /**
     * Закрыть замечание (Resolved → Closed)
     */
    @Transactional
    public TransitionResult close(UUID remarkId, UUID actorId) {
        Remark remark = remarkRepository.findById(remarkId)
            .orElseThrow(() -> new IllegalArgumentException("Remark not found"));
        
        return executeTransition(remark, actorId, TriggerType.USER_ACTION);
    }
    
    /**
     * Пометить как не требующее исправления (Active → NotRequired)
     */
    @Transactional
    public TransitionResult markNotRequired(UUID remarkId, UUID actorId) {
        Remark remark = remarkRepository.findById(remarkId)
            .orElseThrow(() -> new IllegalArgumentException("Remark not found"));
        
        return executeTransition(remark, actorId, TriggerType.USER_ACTION);
    }
    
    private TransitionResult executeTransition(
        Remark remark, 
        UUID actorId, 
        TriggerType trigger
    ) {
        StateMachineConfig remarkConfig = configRepository
            .findByEntityTypeAndProcessTypeAndVersion(
                EntityType.REMARK, 
                remark.getProcess().getProcessType(), 
                remark.getProcess().getConfigVersion()
            )
            .orElseThrow(() -> new IllegalStateException("Remark config not found"));
        
        TransitionContext context = new TransitionContext(
            remark, actorId, ActorType.USER, Map.of()
        );
        
        return transitionEngine.transition(
            EntityType.REMARK,
            remark.getId(),
            remarkConfig.getId(),
            remark.getStatus(),
            trigger,
            context,
            remark::setStatus
        );
    }
}
```

### 5. Обновление guard `AllRemarksProcessed`

Из Фазы 8 guard был заглушкой (всегда `true`). Теперь реальная проверка:

```java
@Component("allRemarksProcessedGuard")
public class AllRemarksProcessedGuard implements Guard {
    
    private final RemarkRepository remarkRepository;
    
    @Override
    public boolean execute(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();
        
        // Найти все замечания процесса
        List<Remark> remarks = remarkRepository.findByProcessId(process.getId());
        
        if (remarks.isEmpty()) {
            return true;  // нет замечаний — разрешить возобновление
        }
        
        // Проверить, что все замечания обработаны (NOT IN [Active, InProgress])
        return remarks.stream().allMatch(remark -> {
            String status = remark.getStatus();
            return !"Active".equals(status) && !"InProgress".equals(status);
        });
    }
}
```

**Статус `InProgress`** для замечания не используется в текущей таблице переходов (источник: `04_state_machines.md` §8.2), но guard проверяет на всякий случай (защита от будущих расширений).

### 6. Таблица `remark` (справочно, схема уже применена миграцией V7 из Фазы 1)

```sql
create table remark (
    id                      uuid primary key,
    process_id              uuid not null references process_instance(id),
    stage_id                uuid not null references stage_instance(id),
    participant_id          uuid null references participant(id),
    text                    text not null,
    status                  varchar(50) not null
                            check (status in ('Created', 'Active', 'Resolved', 'Rejected', 
                                              'Closed', 'NotRequired')),
    created_at              timestamptz not null default now(),
    created_by              uuid not null,
    resolved_at             timestamptz null,
    resolved_by             uuid null,
    closed_at               timestamptz null,
    closed_by               uuid null
);
create index ix_remark_process on remark(process_id);
create index ix_remark_stage on remark(stage_id);
create index ix_remark_status on remark(status);
```

`RemarkRepository` расширяется методами:
- `List<Remark> findByProcessId(UUID processId)`
- `List<Remark> findByStageIdAndStatus(UUID stageId, String status)`

### 7. Бизнес-правила замечаний (справка)

(источник: `business_context_v2.0.md` §21)

- **Кто может создать:** любой участник этапа (проверка через guard `IsParticipant`).
- **Обязательность при Reject:** при решении `REJECT` участник должен оставить комментарий **или** создать замечание. В Фазе 5 валидация упрощена (комментарий обязателен), в этой фазе можно расширить: если есть замечание, комментарий не обязателен. **Упрощение PHASE-11:** валидация остаётся как в Фазе 5 (комментарий обязателен), расширение — в будущем.
- **Автоматическое закрытие:** при закрытии этапа (Approved/ApprovedWithComments) все Active замечания автоматически переходят в Closed (action `AutoProcessOpenRemarks`).
- **Блокировка возобновления:** процесс нельзя возобновить (OnRework → InProgress), пока есть замечания в статусе Active (guard `AllRemarksProcessed`).

## Явно не входит (Out of scope)

- REST API для замечаний (`POST /processes/{id}/remarks`, `PUT /remarks/{id}/resolve`, etc.) — PHASE-23
- Файловые вложения в замечаниях (поле `attachments` в таблице `remark` — не используется) — будущая фаза или вне объёма
- Публикация событий `approval.remark.created` в Outbox — PHASE-19 (Event Publisher)
- Проверка `IsParticipant` через реальное чтение процесса — упрощена до заглушки
- Расширение валидации Reject (замечание ИЛИ комментарий) — остаётся как в Фазе 5
- Замечания уровня процесса (без привязки к этапу) — не предусмотрено спецификацией

## Что уже есть в репозитории на момент постановки

- `Remark` (`domain/process/`) — JPA-сущность создана в Фазе 1, не используется кодом
- `Comment`, `CommentRepository` (Фаза 5) — работает, комментарии создаются при решении
- `AllRemarksProcessedGuard` (Фаза 8) — заглушка, обновляется в этой фазе
- `TransitionEngine` (Фаза 2) — поддерживает вложенные вызовы (пример: `AutoProcessOpenRemarks` вызывает `AutoProcessRemark`)
- Guard `IsInitiator` (Фаза 3) — переиспользуется для `MarkNotRequired`, `ResolveRemark`
- `EntityType.REMARK` (Фаза 1) — enum уже существует

## Технические требования

1. Новый сервис `RemarkService` (`service`) — методы `create()`, `resolve()`, `reject()`, `close()`, `markNotRequired()`.

2. Расширение `RemarkRepository`:
   - `List<Remark> findByProcessId(UUID processId)`
   - `List<Remark> findByStageIdAndStatus(UUID stageId, String status)`

3. Guard-бины (`service.guard`):
   - `IsRemarkAuthorGuard` (бин `isRemarkAuthorGuard`)
   - `StageNotClosedGuard` (бин `stageNotClosedGuard`)
   - `IsParticipantGuard` (бин `isParticipantGuard`) — заглушка
   - Обновление `AllRemarksProcessedGuard` — реальная проверка через `RemarkRepository`

4. Action-бины (`service.action`):
   - `NotifyInitiatorAction` (бин `notifyInitiatorAction`) — заглушка
   - `AutoProcessOpenRemarksAction` (бин `autoProcessOpenRemarksAction`) — внедряет `RemarkRepository`, `StateMachineConfigRepository`, `TransitionEngine`

5. Новая миграция `V24__seed_remark_state_machine.sql`:
   - `guard_registry`: `IsRemarkAuthor`, `StageNotClosed`, `IsParticipant`
   - `action_registry`: `NotifyInitiator`, `AutoProcessOpenRemarks`
   - `status_registry`: `('Created', 'REMARK', ...)`, `('Active', 'REMARK', ...)`, `('Resolved', 'REMARK', ...)`, `('Closed', 'REMARK', ...)`, `('NotRequired', 'REMARK', ...)`
   - `state_machine_config` для REMARK (STANDARD, version=1)
   - `state_config` (Created, Active, Resolved, Closed, NotRequired, Rejected)
   - `transition_config` (все переходы из таблицы §1)
   - `UPDATE transition_config SET actions = array_append(actions, 'AutoProcessOpenRemarks') WHERE code IN ('StageApproved', 'StageApprovedWithComments')` — добавление action в закрытие этапа (миграции V21/V22)

6. `RemarkService.create()` не вызывает `TransitionEngine` для перехода `CreateRemark` — сразу создаёт запись с `status = "Created"`, затем вызывает `ActivateRemark` (Created → Active).

## Критерии приёмки

1. **Создание замечания:** участник вызывает `RemarkService.create(processId, stageId, participantId, text, actorId)` → создана запись `remark` с `status = "Active"` (не Created — автоматически активировано), `createdBy = actorId`.

2. **Исправление замечания:** инициатор вызывает `resolve(remarkId)` → замечание переходит в статус `"Resolved"`, `resolvedAt` установлен, `resolvedBy = actorId`.

3. **Отклонение исправления:** автор замечания вызывает `reject(remarkId)` → замечание возвращается в статус `"Active"`.

4. **Закрытие замечания:** автор замечания вызывает `close(remarkId)` → замечание переходит в статус `"Closed"`, `closedAt` установлен, `closedBy = actorId`.

5. **Пометка как не требующее исправления:** инициатор вызывает `markNotRequired(remarkId)` → замечание переходит в статус `"NotRequired"`.

6. **Блокировка возобновления:** процесс OnRework с активным замечанием — попытка `ProcessService.resume()` → переход не выполняется (`TransitionResult.performed() == false`), guard `AllRemarksProcessed` блокирует.

7. **Возобновление после обработки замечаний:** все замечания Resolved/Closed/NotRequired → `resume()` выполняется успешно.

8. **Автоматическое закрытие замечаний:** этап с двумя активными замечаниями закрывается (Approved) → оба замечания автоматически переходят в статус `"Closed"`, action `AutoProcessOpenRemarks` выполнен.

9. **Guard блокирует non-автора:** попытка `close(remarkId)` с `actorId`, не равным `remark.createdBy` → переход не выполняется, guard `IsRemarkAuthor` блокирует.

## Тестирование

- Юнит-тесты guard-бинов:
  - `IsRemarkAuthorGuardTest` — вручную собранный `Remark`: `actorId` совпадает/не совпадает с `createdBy`.
  - `StageNotClosedGuardTest` — замечание с этапом в разных статусах: Active (true), Approved (false), OnRework (false).
  - `AllRemarksProcessedGuardTest` — с mock-`RemarkRepository`: нет замечаний, все Resolved, есть Active.

- Юнит-тесты action-бинов:
  - `AutoProcessOpenRemarksActionTest` — с mock-`TransitionEngine`, проверка вызова `transition()` для каждого Active замечания.

- Интеграционный тест `RemarkServiceIntegrationTest` (Testcontainers, реальные миграции `V1`–`V24`):
  - **Полный lifecycle замечания:** создать процесс, участник создаёт замечание → инициатор исправляет (`resolve`) → автор закрывает (`close`) → замечание Closed.
  - **Отклонение исправления:** инициатор исправляет → автор отклоняет (`reject`) → замечание снова Active → инициатор исправляет повторно → автор закрывает.
  - **Блокировка возобновления:** процесс OnRework с Active замечанием → попытка `resume()` блокирована → инициатор исправляет замечание → `resume()` проходит.
  - **Автоматическое закрытие:** этап с активными замечаниями согласован → замечания автоматически Closed.
  - Критерии приёмки 1–9.

## Открытые вопросы

1. **Статус `InProgress` для замечания** — в текущей таблице переходов отсутствует, но guard `AllRemarksProcessed` проверяет. Нужен ли этот статус? Решение: оставить проверку (защита от будущих расширений), но не создавать переходы с этим статусом в V24.

2. **Валидация Reject: комментарий ИЛИ замечание** — в Фазе 5 валидация упрощена (комментарий обязателен). Расширить в этой фазе (если есть замечание, комментарий не обязателен)? Решение: оставить как есть в Фазе 5, расширение — в будущей фазе (PHASE-28) или вне объёма.

3. **Guard `IsParticipant` для `CreateRemark`** — заглушка (всегда `true`). Реализовать полную проверку (внедрить `ProcessRepository` в guard, проверить участие actorId в процессе)? Решение принимает Claude Code — если усложняет архитектуру (guards без репозиториев), оставить заглушку и зафиксировать как технический долг.

## Ревью Cowork

*(Заполняется после реализации и проверки)*
