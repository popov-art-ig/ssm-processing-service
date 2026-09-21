# PHASE-05 — Принятие решения участником (Decide, ParticipantStateMachine)

> См. `doc/tickets/TEMPLATE.md` за описанием процесса. Тикет самодостаточен: весь материал
> скопирован ниже. Пометки вида «(источник: `04_state_machines.md` §7)» — это атрибуция
> для истории/ревью, не рабочие ссылки — у вас нет доступа к этим файлам.
>
> Подготовлен на основе завершённых фаз 1-4 и анализа архитектурной документации.

## Статус

Не начато

## Контекст

Фаза 4 реализовала активацию первого этапа и назначение задач участникам (Pending → Active для этапа, Pending/Assigned для участников). Но **участники не могут принимать решения** — нет `DecisionService`, нет переходов `ParticipantStateMachine`, таблица `decision` не используется.

Эта фаза закрывает этот пробел: участник может принять одно из трёх решений (Approve, ApproveWithComments, Reject), решение записывается в БД, статус участника меняется на Decided. **Этап остаётся Active** — агрегация решений и закрытие этапа выносятся в PHASE-06.

**Явно НЕ входит:** агрегация решений участников (этап не закрывается), автосогласование по срокам, Sequential execution order (назначение следующего участника после решения текущего), полный lifecycle `ParticipantStateMachine` (только переход Decide).

## Источники (для истории, не для перехода)

- `04_state_machines.md` §7 (ParticipantStateMachine, состояния и переходы)
- `05_guards_actions_registry.md` §4.3 (guards участника), §5.4 (actions участника)
- `08_db_schema.md` §9 (таблица `decision`), §10 (таблица `comment`)
- `business_context_v2.0.md` §21.4 (бизнес-правила замечаний: при Отклонить обязательны)

## Объём фазы (Scope)

### 1. Переход `Decide` — что именно реализуется

Из таблицы переходов `ParticipantStateMachine` (источник: `04_state_machines.md` §7.2):

```
| Код    | From     | To      | Trigger      | Guards              |
|--------|----------|---------|--------------|---------------------|
| Decide | Assigned | Decided | userAction   | IsAssignedParticipant |
```

**В этой фазе:** только переход `Decide`. Переходы `AssignTask` (Pending → Assigned), `StartWork` (Assigned → InProgress), `RevokeTask`, `AutoApprove` — вне объёма (первый уже реализован в PHASE-04 как часть AssignParticipantTasks, остальные — в будущих фазах).

### 2. Guards

**G-PA-001 `IsAssignedParticipant`** (источник: `05_guards_actions_registry.md` §4.3):

> **Что проверяет.** Текущий пользователь — назначенный участник с правом принять решение.
>
> **Логика.**
> ```
> context.currentUser.id == participant.userId
> AND participant.status == "Assigned"
> ```

Реализация:
```
execute(context):
    participant = (Participant) context.entity()
    return context.actorId() == participant.userId 
           && "Assigned".equals(participant.status)
```

### 3. Actions

**A-PA-001 `RecordDecision`** (источник: `05_guards_actions_registry.md` §5.4):

> **Что делает.** Записывает решение участника в таблицу `decision`.
>
> **Логика.**
> ```
> decision = new Decision()
> decision.participantId = participant.id
> decision.type = decisionType  // Approve | ApproveWithComments | Reject
> decision.comment = context.parameters["comment"]
> decision.decidedAt = now
> decision.decidedBy = context.currentUser.id
> save(decision)
> ```

**A-PA-002 `RecordComment`** (источник: `05_guards_actions_registry.md` §5.4):

> **Что делает.** Записывает комментарий участника (если есть).
>
> **Логика.**
> ```
> if context.parameters["comment"] != null:
>     comment = new Comment()
>     comment.processId = participant.process.id
>     comment.stageId = participant.stage.id
>     comment.participantId = participant.id
>     comment.text = context.parameters["comment"]
>     comment.createdAt = now
>     comment.createdBy = context.currentUser.id
>     save(comment)
> ```

**A-PA-003 `UpdateParticipantStatus`** — НЕ отдельный action-бин, статус пишет сам `TransitionEngine` через `statusWriter` (как и в предыдущих фазах). Но для единообразия с именованием спеки можно зарегистрировать no-op action в `action_registry` с пометкой, что это делает движок.

### 4. `DecisionService` — use-case метод

```java
@Service
public class DecisionService {
    
    @Transactional
    public TransitionResult decide(
        UUID processId, 
        UUID stageId, 
        UUID participantId, 
        DecisionType decisionType,  // enum: APPROVE, APPROVE_WITH_COMMENTS, REJECT
        String comment,
        UUID actorId
    ) {
        // 1. Загрузить ProcessInstance (для навигации к participant через stages)
        ProcessInstance process = processRepository.findById(processId)
            .orElseThrow(() -> new IllegalArgumentException("Process not found"));
        
        // 2. Найти StageInstance
        StageInstance stage = process.getStages().stream()
            .filter(s -> s.getId().equals(stageId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Stage not found"));
        
        // 3. Найти Participant в текущей итерации
        StageIteration currentIteration = stage.getIterations().stream()
            .max(Comparator.comparing(StageIteration::getIterationIdx))
            .orElseThrow(() -> new IllegalStateException("No iterations"));
        
        Participant participant = currentIteration.getParticipants().stream()
            .filter(p -> p.getId().equals(participantId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Participant not found"));
        
        // 4. Резолвить configId для PARTICIPANT state machine
        StateMachineConfig participantConfig = stateMachineConfigRepository
            .findByEntityTypeAndProcessTypeAndVersion(
                EntityType.PARTICIPANT, 
                process.getProcessType(), 
                process.getConfigVersion()
            )
            .orElseThrow(() -> new IllegalStateException("Participant config not found"));
        
        // 5. Собрать TransitionContext с decisionType и comment в parameters
        Map<String, Object> parameters = Map.of(
            "decisionType", decisionType,
            "comment", comment != null ? comment : ""
        );
        TransitionContext context = new TransitionContext(
            participant, actorId, ActorType.USER, parameters
        );
        
        // 6. Вызвать TransitionEngine
        return transitionEngine.transition(
            EntityType.PARTICIPANT,
            participant.getId(),
            participantConfig.getId(),
            participant.getStatus(),
            TriggerType.USER_ACTION,
            context,
            participant::setStatus
        );
    }
}
```

**Валидация (источник: `business_context_v2.0.md` §22):**

- При `Approve`: комментарий НЕ обязателен.
- При `ApproveWithComments`: комментарий **обязателен** (иначе `IllegalArgumentException` до вызова движка).
- При `Reject`: комментарий **или замечание** обязательны. В этой фазе замечания не реализованы (PHASE-11), поэтому требуем хотя бы комментарий.

Валидация выполняется в `DecisionService.decide()` до вызова движка:
```java
if (decisionType == DecisionType.APPROVE_WITH_COMMENTS && (comment == null || comment.isBlank())) {
    throw new IllegalArgumentException("Comment is required for ApproveWithComments");
}
if (decisionType == DecisionType.REJECT && (comment == null || comment.isBlank())) {
    throw new IllegalArgumentException("Comment or remark is required for Reject");
}
```

### 5. Таблица `decision` (справочно, схема уже применена миграцией V5 из Фазы 1)

```sql
create table decision (
    id                      uuid primary key,
    participant_id          uuid not null references participant(id),
    decision_type           varchar(50) not null
                            check (decision_type in ('APPROVE', 'APPROVE_WITH_COMMENTS', 'REJECT')),
    comment                 text null,
    decided_at              timestamptz not null default now(),
    decided_by              uuid not null,
    created_at              timestamptz not null default now()
);
create index ix_decision_participant on decision(participant_id);
```

Используется через обычный `DecisionRepository extends JpaRepository<Decision, UUID>` — создаётся в `RecordDecisionAction`.

### 6. Таблица `comment` (справочно, схема уже применена миграцией V6 из Фазы 1)

```sql
create table comment (
    id                      uuid primary key,
    process_id              uuid not null references process_instance(id),
    stage_id                uuid null references stage_instance(id),
    participant_id          uuid null references participant(id),
    text                    text not null,
    created_at              timestamptz not null default now(),
    created_by              uuid not null
);
```

Используется через `CommentRepository extends JpaRepository<Comment, UUID>` — создаётся в `RecordCommentAction`.

## Явно не входит (Out of scope)

- Агрегация решений (`EvaluateAggregation`, guards `AllParticipantsDecided`/`AggregationApproved` и т.д.) — PHASE-06
- Закрытие этапа (`StageApproved`, `StageOnRework`) — PHASE-06
- Автосогласование по срокам (`AutoApproveParticipant`, trigger TIMER) — PHASE-16
- Sequential execution order: `AssignNextParticipantTask` после решения — PHASE-27
- Полные переходы `ParticipantStateMachine` (`AssignTask` — уже реализован в PHASE-04, `StartWork`/`RevokeTask`/`CancelTask` — будущие фазы)
- Замечания (таблица `remark`, `RemarkStateMachine`) — PHASE-11 (в этой фазе только комментарии, валидация Reject упрощена)
- REST API — PHASE-22
- Публикация событий `approval.decision.recorded` в Outbox — PHASE-19 (Event Publisher)

## Что уже есть в репозитории на момент постановки

- `ProcessInstance`, `StageInstance`, `StageIteration`, `Participant` (`domain/process/`) — все нужные поля есть: `status`, `userId`, навигация по связям
- `Decision`, `Comment` (`domain/process/`) — JPA-сущности уже созданы в Фазе 1, не используются ни одним кодом (первое использование — в этой фазе)
- `ProcessService` (Фаза 3, `service/ProcessService.java`) — пример use-case сервиса (не меняется)
- `TransitionEngine`/`ModelFactory`/`ComponentResolver` (Фаза 2) — используются как есть
- `StateMachineConfigRepository.findByEntityTypeAndProcessTypeAndVersion` (Фаза 3) — переиспользуется для резолвинга PARTICIPANT-конфига
- Guard/action-бины (`service.guard`, `service.action`) — новые бины этой фазы добавляются рядом
- `DecisionType` (`domain/common/DecisionType.java`) — enum уже существует (Фаза 1): `APPROVE`, `APPROVE_WITH_COMMENTS`, `REJECT`

## Технические требования

1. Новый сервис `DecisionService` (`ru.coordination.approval.service`) — метод `decide(processId, stageId, participantId, decisionType, comment, actorId)` → `TransitionResult`.
2. Новые репозитории:
   - `DecisionRepository extends JpaRepository<Decision, UUID>` (`domain.process` — рядом с сущностью)
   - `CommentRepository extends JpaRepository<Comment, UUID>` (`domain.process`)
3. Guard-бин `IsAssignedParticipantGuard` (`service.guard`, бин `isAssignedParticipantGuard`) — реализует `Guard`, приводит `context.entity()` к `Participant`.
4. Action-бины:
   - `RecordDecisionAction` (`service.action`, бин `recordDecisionAction`) — читает `decisionType`/`comment` из `context.parameters()`, создаёт `Decision`, сохраняет через `DecisionRepository`.
   - `RecordCommentAction` (`service.action`, бин `recordCommentAction`) — если `comment` не пуст, создаёт `Comment`, сохраняет через `CommentRepository`.
5. Новая миграция `V20__seed_decide_transition.sql`:
   - `guard_registry`: `IsAssignedParticipant` → `isAssignedParticipantGuard`
   - `action_registry`: `RecordDecision` → `recordDecisionAction`, `RecordComment` → `recordCommentAction`
   - `status_registry`: `('Assigned', 'PARTICIPANT', ...)`, `('Decided', 'PARTICIPANT', ...)`
   - `state_machine_config` + `state_config` (Assigned, Decided) + `transition_config` (`Decide`, `Assigned → Decided`, `USER_ACTION`, guards `{IsAssignedParticipant}`, actions `{RecordDecision,RecordComment}`, emits `{approval.decision.recorded}`) для `entity_type='PARTICIPANT', process_type='STANDARD', version=1`
6. `RecordDecisionAction` внедряет `DecisionRepository`, `RecordCommentAction` — `CommentRepository`; оба читают участника через `(Participant) context.entity()` и навигацию по связям к процессу/этапу.

## Критерии приёмки

1. `DecisionService.decide(...)` с `decisionType = APPROVE` и валидными ID — участник переходит в статус `"Decided"`, создана запись `decision` с `decision_type = 'APPROVE'`, возвращён `TransitionResult.performed() == true` с `emittedEvents() == ["approval.decision.recorded"]`.
2. То же с `decisionType = APPROVE_WITH_COMMENTS` и непустым `comment` — создана `decision` + создан `comment`, `participant.status = "Decided"`.
3. То же с `decisionType = REJECT` и непустым `comment` — создана `decision` с `decision_type = 'REJECT'` + `comment`, участник `"Decided"`.
4. Вызов с `APPROVE_WITH_COMMENTS` и `comment == null` (или пустой строкой) — `IllegalArgumentException` **до вызова движка** (валидация в `DecisionService`).
5. Вызов с `REJECT` и `comment == null` — `IllegalArgumentException`.
6. Вызов с `actorId`, не равным `participant.userId` — переход не выполняется (`TransitionResult.performed() == false`), guard `IsAssignedParticipant` блокирует.
7. Вызов на участнике в статусе `"Pending"` (не `"Assigned"`) — переход не выполняется (guard блокирует).
8. Повторный вызов `decide` на уже `"Decided"` участнике — либо `NoApplicableTransitionException` (нет перехода из `Decided` по `Decide`/`USER_ACTION` для этого конфига), либо иное явное поведение (задокументировать выбор в PR, как в PHASE-03).

## Тестирование

- Юнит-тесты:
  - `IsAssignedParticipantGuardTest` — без БД, вручную собранный `Participant` через `@SuperBuilder`: случаи `actorId` совпадает/не совпадает с `userId`, статус `Assigned`/`Pending`/`Decided`.
  - `RecordDecisionActionTest`, `RecordCommentActionTest` — с mock-репозиториями (Mockito), проверка что вызваны `save()` с правильными полями.
- Интеграционный тест `DecisionServiceIntegrationTest` (Testcontainers, реальные миграции `V1`–`V20`):
  - Подготовить процесс с активным этапом и одним участником в статусе `Assigned` (переиспользовать фикстуру из `ProcessServiceIntegrationTest` Фазы 4, расширив её).
  - Вызвать `DecisionService.decide(...)` с каждым из трёх `DecisionType`.
  - Проверить критерии приёмки 1–8 через реальные запросы к БД (`DecisionRepository.findAll()`, `participant.getStatus()`).

## Открытые вопросы

1. Поведение при повторном `decide` на уже `"Decided"` участнике (критерий приёмки 8) — решение принимает Claude Code (по аналогии с открытым вопросом №2 PHASE-03: скорее всего `NoApplicableTransitionException`, если в V20 не будет перехода из `Decided`).
2. Полная реализация валидации "комментарий ИЛИ замечание" для `Reject` требует таблицы `remark` (PHASE-11) — в этой фазе упрощаем до "комментарий обязателен". Когда появится PHASE-11, валидацию нужно расширить (это стоит явно зафиксировать в тикете PHASE-11).
3. `RecordCommentAction` создаёт комментарий с привязкой к `processId`, `stageId`, `participantId` — но `Comment.stageId` и `Comment.participantId` в схеме `nullable` (могут быть комментарии уровня процесса, без привязки к конкретному участнику). В этой фазе комментарии создаются только при решении участника, поэтому все три ID заполнены. Комментарии уровня процесса/этапа (без участника) — в будущих фазах или вне объёма.

## Ревью Cowork

*(Заполняется после реализации и проверки)*
