# PHASE-03 — ProcessService и запуск процесса (StartProcess, STANDARD)

> См. `doc/tickets/TEMPLATE.md` за описанием процесса. Тикет самодостаточен: весь материал
> скопирован ниже. Пометки вида «(источник: `05_guards_actions_registry.md` §9.1)» — это
> атрибуция для истории/ревью, не рабочие ссылки — у вас нет доступа к этим файлам.
>
> Подготовлен по итогам ревью Фазы 2 (PR #3, `9d4c9b1`, смержено без расхождений уровня ADR
> — см. «Ревью Cowork» в конце `doc/tickets/PHASE-02-state-machine-engine.md`). Объём выбран
> пользователем как «вертикальный срез»: минимальный сквозной путь через уже готовый
> `TransitionEngine`, а не широкий каталог guards/actions и не скелеты всех 14 доменных
> сервисов сразу.

## Статус

На проверке (PR открыт)

## Контекст

Фаза 2 дала работающий `TransitionEngine`, но он никем не вызывается — ни одного доменного
сервиса ещё нет, и ни одна строка в `guard_registry`/`action_registry`/`state_machine_config`
не заполнена (Фаза 1 создала только схему БД, без данных). Эта фаза — первый сквозной путь:
**один процесс, один переход** (`StartProcess`, `Draft → InProgress`, тип процесса
`STANDARD`) — от вызова сервисного метода до записи нового статуса и `AuditEvent` в БД,
через реальный `TransitionEngine`, реальные guards/actions-бины и реальные строки в реестрах.

**Важное сознательное упрощение.** Полный цикл «создать процесс из шаблона» (подбор
шаблона → генерация маршрута → резолвинг ролей → валидация → сохранение) — это
`MatchService`/`RouteGeneratorService`/`RouteValidatorService`, которых нет и не будет в
этой фазе. Поэтому здесь `ProcessInstance`/`StageInstance`/`StageIteration`/`Participant`
для теста **создаются напрямую через репозитории** (как уже готовые, «как будто только что
сгенерированные»), а не через полный доменный флоу создания процесса — тот флоу планируется
отдельной, более поздней фазой (условно PHASE-05 и далее, `RouteGeneratorService` и т.д.).

## Источники (для истории, не для перехода)

- `04_state_machines.md` §4.2 (переходы `ProcessStateMachine`, STANDARD), §3.4
  (Snapshot-on-Start).
- `05_guards_actions_registry.md` §4.1 (условия G-P-001, G-P-004, G-P-005), §5.1
  (действие A-P-002), §9.1 (пример JSON перехода `StartProcess`).
- `10_architecture.md` §6.2.6 (`ProcessService`), §9 (`ProcessInstanceRepository` и др.).
- `08_db_schema.md` §6 (`process_instance`), §7 (`stage_instance`/`stage_iteration`), §8
  (`participant`), §5 (`template`/`stage_template`/`slot_template`) — DDL уже применена
  Фазой 1.
- `doc/tickets/PHASE-02-state-machine-engine.md` — контракт `TransitionEngine`, который эта
  фаза использует как готовый компонент, не меняя его.

## Объём фазы (Scope)

### 1. Переход `StartProcess` — что именно реализуется

Из спецификации (источник: `05_guards_actions_registry.md` §9.1, пример перехода):

```json
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

Из таблицы переходов STANDARD (источник: `04_state_machines.md` §4.2): код `StartProcess`,
`From = Draft`, `To = InProgress`, `Trigger = userAction`.

**В этой фазе транзишн сужен до `actions: ["SetStartedAt"]`** (без `AssignStageTasks` и без
`PublishDomainEvent` как action-бина) — обоснование в разделе 4 ниже. `emits` сохранён как
есть (`["approval.process.started"]") — `TransitionEngine` (Фаза 2) уже возвращает `emits`
из `TransitionResult` без необходимости в отдельном action-публикаторе; сама публикация в
Outbox/RabbitMQ — фаза Event Publisher, не эта.

**Только `processType = STANDARD`.** У `UNIFIED` первый переход другой (`SubmitRoute`,
`Draft → PendingResponsibleApproval` — источник: `04_state_machines.md` §4.3) — вне объёма
этой фазы.

### 2. Guards — условия перехода

Дословные описания (источник: `05_guards_actions_registry.md` §4.1):

- **G-P-001 `IsInitiator`** — «Текущий пользователь — инициатор процесса». Логика:
  `context.currentUser.id == process.initiatorId`.
- **G-P-004 `AllMandatorySlotsFilled`** — «В маршруте заполнены все обязательные слоты».
  Логика по спеке: `∀ stage ∈ route.stages: ∀ slot ∈ stage.actorSlots where slot.required:
  slot.userId != null`.
- **G-P-005 `AllDurationsValid`** — «У всех этапов срок > 0». Логика:
  `∀ stage ∈ route.stages: stage.duration > 0`.

**Упрощение `AllMandatorySlotsFilled` в этой фазе (важно, отличается от буквальной спеки).**
Буквальная логика работает по `SlotTemplate.required` (шаблонное описание слота, поле
`required` — см. `slot_template` в §8.3 ниже) и требует сопоставления
`StageInstance`↔`StageTemplate` — а такого прямого FK в схеме нет (он появляется по смыслу
только когда `RouteGeneratorService` генерирует маршрут, чего в этой фазе нет). Проверять
буквально — значит тянуть `Template`/`StageTemplate`/`SlotTemplate` в объём этой фазы без
реальной пользы (данные всё равно готовятся вручную в тесте). Поэтому здесь guard
проверяет то, что реально смоделировано в `process_instance`/`stage_instance`/
`stage_iteration`/`participant` (все уже существуют, Фаза 1):

```
∀ stage ∈ process.stages where stage.mandatory == true:
    let currentIteration = stage.iterations с максимальным iterationIdx
    currentIteration существует AND currentIteration.participants не пусто
```

То есть: у каждого обязательного этапа (`stage_instance.is_mandatory = true`) в его текущей
(последней) итерации есть хотя бы один участник. Это не тождественно спеке (не проверяет
конкретно обязательность отдельного слота, а только «этап вообще не пуст»), но проверяемо
уже сейчас и не расходится с намерением guard'а («маршрут не запускается с дырами»). Когда
появится `RouteGeneratorService` и реальная связь `Participant.actorSlotRef → slot_template`,
логику этого guard'а нужно будет уточнить до буквальной — это стоит завести отдельным пунктом
в тикете той фазы, а не тихо оставлять как есть.
- **G-P-005 `AllDurationsValid`** — реализуется буквально: `∀ stage ∈ process.stages:
  stage.duration > 0`. Технически уже гарантировано CHECK-констрейнтом
  `stage_instance.duration > 0` (см. `08_db_schema.md`/миграция `V4`), так что guard в
  проде всегда будет `true` — но реализовать его нужно (defensive, и для тестового покрытия
  критерия приёмки с намеренно некорректными данными, собранными в памяти, а не через
  реальный INSERT).

### 3. Action — что выполняется при успешном переходе

**A-P-002 `SetStartedAt`** (источник: `05_guards_actions_registry.md` §5.1) — «Фиксирует
`process.startedAt`». Реализация: `process.setStartedAt(Instant.now())`.

### 4. Почему `AssignStageTasks` и `PublishDomainEvent` НЕ входят в эту фазу

- **`AssignStageTasks`** (A-P-001, «Создаёт задачи участникам активного этапа») по смыслу
  требует активировать первый этап маршрута — а это отдельный переход
  `StageStateMachine` (`ActivateStage`, источник: `04_state_machines.md` §5.2), которого нет
  (движок `STAGE`-конфига не заведён, `StageService` не существует). Установить
  `stage.status` напрямую в обход `TransitionEngine` нарушало бы принцип «состояние меняется
  только через явный переход state machine» (ADR-002/ADR-028, уже закреплённый в реализации
  `TransitionEngine` Фазы 2). Правильная реализация этого action откладывается на фазу,
  вводящую `StageStateMachine`/`StageService` (условно PHASE-04).
- **`PublishDomainEvent`** (A-P-010, «Публикует событие в Outbox») требует `OutboxService`/
  `outbox_event`-репозиторий — Event Publisher, отдельная фаза (см. `10_architecture.md`
  §10), явно не входит ни в Фазу 2, ни сюда. `TransitionEngine` (Фаза 2) уже отдаёт `emits`
  как часть `TransitionResult` без участия action — этого достаточно, чтобы данные не
  терялись, когда Event Publisher появится.

### 5. Реестры и конфигурация — новая Flyway-миграция

Ничего из необходимого для переходов данные не существуют в БД после Фазы 1/2 (только
схема). Нужна новая миграция `V18__seed_start_process_transition.sql`
(`src/main/resources/db/migration/`, следующий номер после `V17__notification_settings.sql`)
со следующими вставками (используя реальные DDL таблиц, уже применённые):

```sql
-- guard_registry (08_db_schema.md §12/16.1)
insert into guard_registry (code, display_name, description, handler, scope, categories, applicable_entities, is_active, created_at, updated_at)
values
    ('IsInitiator', 'Является инициатором', 'Текущий пользователь — инициатор процесса (G-P-001)', 'isInitiatorGuard', 'GLOBAL', '{AUTHORIZATION}', '{PROCESS}', true, now(), now()),
    ('AllMandatorySlotsFilled', 'Все обязательные слоты заполнены', 'Упрощённая реализация PHASE-03: у каждого обязательного этапа есть участник в текущей итерации (G-P-004, буквальная per-slot проверка — в фазе RouteGeneratorService)', 'allMandatorySlotsFilledGuard', 'GLOBAL', '{VALIDATION}', '{PROCESS}', true, now(), now()),
    ('AllDurationsValid', 'Все сроки корректны', 'У всех этапов срок > 0 (G-P-005)', 'allDurationsValidGuard', 'GLOBAL', '{VALIDATION}', '{PROCESS}', true, now(), now());

-- action_registry (08_db_schema.md §12/16.2)
insert into action_registry (code, display_name, description, handler, scope, categories, applicable_entities, is_active, created_at, updated_at)
values
    ('SetStartedAt', 'Установить время старта', 'Фиксирует process.startedAt (A-P-002)', 'setStartedAtAction', 'GLOBAL', '{DATA}', '{PROCESS}', true, now(), now());

-- status_registry (08_db_schema.md §5.1) — коды состояний ProcessStateMachine, задействованные в этой фазе
insert into status_registry (code, entity_type, display_name, is_terminal, is_active, created_at, updated_at)
values
    ('Draft', 'PROCESS', 'Черновик', false, true, now(), now()),
    ('InProgress', 'PROCESS', 'В процессе', false, true, now(), now());

-- state_machine_config + state_config + transition_config (08_db_schema.md §15)
insert into state_machine_config (id, version, entity_type, process_type, status, active_process_count, created_at, created_by, published_at, published_by, updated_at, version_lock)
values ('11111111-0003-0000-0000-000000000001', 1, 'PROCESS', 'STANDARD', 'PUBLISHED', 0, now(), '00000000-0000-0000-0000-000000000000', now(), '00000000-0000-0000-0000-000000000000', now(), 0);

insert into state_config (id, config_id, code, display_name, is_initial, is_terminal, metadata, created_at)
values
    ('11111111-0003-0000-0000-000000000002', '11111111-0003-0000-0000-000000000001', 'Draft', 'Черновик', true, false, '{}', now()),
    ('11111111-0003-0000-0000-000000000003', '11111111-0003-0000-0000-000000000001', 'InProgress', 'В процессе', false, false, '{}', now());

insert into transition_config (id, config_id, code, from_state, to_state, trigger, guards, actions, emits, priority, is_active, created_at)
values (
    '11111111-0003-0000-0000-000000000004', '11111111-0003-0000-0000-000000000001',
    'StartProcess', 'Draft', 'InProgress', 'USER_ACTION',
    '{IsInitiator,AllMandatorySlotsFilled,AllDurationsValid}',
    '{SetStartedAt}',
    '{approval.process.started}',
    0, true, now()
);
```

Конкретные UUID-константы выше — ориентир (детерминированные, чтобы их было легко узнать в
тестах); Claude Code может заменить на `gen_random_uuid()`/собственные константы, если так
удобнее — это не принципиально, важно только что миграция единственная и содержит все три
уровня (guard/action, status, config+states+transition) согласованно.

## Явно не входит (Out of scope)

- `MatchService`, `RouteGeneratorService`, `RouteEditorService`, `RouteValidatorService` —
  создание процесса «с нуля» из шаблона. `ProcessInstance` и его `stages`/`iterations`/
  `participants` в этой фазе готовятся напрямую через репозитории (в тестах и, при
  необходимости, в служебном seed-коде), а не через доменный флоу создания.
  `POST /processes` (`07_api_contract.md`) не реализуется.
- `StageService`/`StageStateMachine` — переход `ActivateStage` и вся активация этапов; из-за
  этого `AssignStageTasks` не входит в объём (см. раздел 4).
  `DecisionService`, любые последующие переходы `ProcessStateMachine` после `StartProcess`
  (`ResumeProcess`, `RecallProcess` и т.д.) — только `StartProcess`.
- Event Publisher (`OutboxService`, публикация в RabbitMQ) — `PublishDomainEvent` не
  реализуется как action; `emits` только возвращается в `TransitionResult`.
- REST API — эта фаза только доменный сервис, вызываемый напрямую (из теста/будущего
  контроллера).
- `UNIFIED` тип процесса — только `STANDARD`.
- Буквальная per-slot реализация `AllMandatorySlotsFilled` (через `SlotTemplate.required`) —
  см. упрощение в разделе 2.

## Что уже есть в репозитории на момент постановки

Готово и не требует изменений (Фазы 1–2), все пути реальны:

- `src/main/java/ru/coordination/approval/engine/TransitionEngine.java` — публичный метод
  `transition(EntityType, UUID entityId, UUID configId, String fromState, TriggerType,
  TransitionContext, Consumer<String> statusWriter)` → `TransitionResult`. Эта фаза только
  вызывает его, не меняет.
- `src/main/java/ru/coordination/approval/engine/TransitionContext.java` — `record(Object
  entity, UUID actorId, ActorType actorType, Map<String,Object> parameters)`.
- `src/main/java/ru/coordination/approval/engine/registry/{Guard,Action}.java` —
  `@FunctionalInterface`: `Guard.evaluate(TransitionContext): boolean`,
  `Action.execute(TransitionContext): void`.
- `src/main/java/ru/coordination/approval/engine/model/StateMachineConfigRepository.java` —
  сейчас пустой (`extends JpaRepository<StateMachineConfig, UUID>`, только `findById`). Этой
  фазе нужен доп. метод — см. «Технические требования» п.2.
- `src/main/java/ru/coordination/approval/domain/process/ProcessInstance.java` — поля
  `id`, `templateRef`, `processType` (enum `STANDARD`/`UNIFIED`), `configVersion` (Integer —
  бизнес-версия `state_machine_config.version`, НЕ его UUID), `status` (String), `initiatorId`,
  `startedAt`, `version` (`@Version`, оптимистичная блокировка), `stages` (`List<StageInstance>`,
  `@OneToMany(cascade=ALL, fetch=LAZY)`).
- `src/main/java/ru/coordination/approval/domain/process/StageInstance.java` — поля
  `duration` (Integer, DB CHECK `> 0`), `mandatory` (boolean, `is_mandatory`), `iterations`
  (`List<StageIteration>`, `@OneToMany`).
- `src/main/java/ru/coordination/approval/domain/process/StageIteration.java` — поля
  `iterationIdx`, `participants` (`List<Participant>`, `@OneToMany`).
- `src/main/java/ru/coordination/approval/domain/process/Participant.java` — поле `userId`
  (UUID, NOT NULL — участник без пользователя в БД невозможен по схеме).
- `src/main/java/ru/coordination/approval/domain/common/{EntityType,ProcessType,
  LifecycleStatus}.java` — enum'ы, уже содержат нужные значения (`EntityType.PROCESS`,
  `ProcessType.STANDARD`, `LifecycleStatus.PUBLISHED`).
- `src/main/java/ru/coordination/approval/domain/audit/ActorType.java` — `USER`/`SYSTEM`/
  `TIMER`.
- **Репозиториев для `domain.process.*` пока нет ни одного** (ни `ProcessInstanceRepository`,
  ни для `StageInstance`/`StageIteration`/`Participant`) — эта фаза добавляет минимум
  необходимое (см. ниже), не весь набор из `10_architecture.md` §9.1.
- `src/main/resources/db/migration/V17__notification_settings.sql` — последняя существующая
  миграция; новая — `V18`.

## Технические требования

1. Новый top-level пакет `ru.coordination.approval.service` для доменных сервисов (аналог
   `ru.coordination.approval.engine` из Фазы 2, но для Domain Core, `10_architecture.md` §6).
   `ProcessService` — единственный класс в этой фазе, метод:
   ```java
   TransitionResult startProcess(UUID processId, UUID actorId)
   ```
2. `ProcessService.startProcess`:
   - Загружает `ProcessInstance` по id (новый `ProcessRepository extends
     JpaRepository<ProcessInstance, UUID>` — разместить рядом с `ProcessService`
     (`service`) либо рядом с сущностью (`domain.process`) — на усмотрение Claude Code,
     задокументировать в PR, аналогично решению по пакетам в Фазе 2).
   - Резолвит `configId`: `StateMachineConfigRepository` (Фаза 2, `engine/model`) — добавить
     метод `Optional<StateMachineConfig> findByEntityTypeAndProcessTypeAndVersion(EntityType,
     ProcessType, Integer)` и найти конфиг по
     `(EntityType.PROCESS, process.getProcessType(), process.getConfigVersion())`. Если не
     найден — понятная ошибка (например, `IllegalStateException`), не `NullPointerException`.
   - Собирает `TransitionContext(process, actorId, ActorType.USER, Map.of())`.
   - Вызывает `transitionEngine.transition(EntityType.PROCESS, process.getId(), configId,
     process.getStatus(), TriggerType.USER_ACTION, context, process::setStatus)`.
   - Метод — `@Transactional` (или полагается на распространение транзакции из
     `TransitionEngine`, которая уже `@Transactional` — на усмотрение Claude Code, главное,
     чтобы загрузка процесса, вызов движка и commit были одной транзакцией — как и в Фазе 2,
     оптимистичная блокировка полагается на границу `@Transactional`, без ручного
     перехвата).
3. Guard-бины (`ru.coordination.approval.service.guard` или рядом с `ProcessService` — на
   усмотрение) реализуют `ru.coordination.approval.engine.registry.Guard`, регистрируются
   как Spring-бины с именами, совпадающими с `handler` из миграции (`isInitiatorGuard`,
   `allMandatorySlotsFilledGuard`, `allDurationsValidGuard`) — `ComponentResolver` (Фаза 2)
   резолвит `handler` сначала как имя бина.
4. Action-бин `setStartedAtAction` — аналогично, реализует `Action`.
5. Guards/actions читают сущность через `(ProcessInstance) context.entity()` — приведение
   типа внутри бина, не в `TransitionContext`/движке (контракт `TransitionContext.entity` —
   `Object`, зафиксирован Фазой 2, не меняется).
6. Не трогать `TransitionEngine`/`ModelFactory`/`ComponentResolver`/`GuardRegistry`/
   `ActionRegistry` из Фазы 2 — эта фаза только использует их как готовый API.

## Критерии приёмки

1. Для `ProcessInstance` со `status = "Draft"`, `processType = STANDARD`, `configVersion`,
   указывающим на опубликованный конфиг из миграции `V18`, и хотя бы одним `StageInstance`
   (`mandatory = true`, `duration > 0`) с одной `StageIteration` и одним `Participant`:
   вызов `ProcessService.startProcess(processId, initiatorId)` переводит `status` в
   `"InProgress"`, устанавливает `startedAt`, создаёт `AuditEvent` (`action = "StartProcess"`),
   возвращает `TransitionResult.performed() == true` с `emittedEvents() ==
   ["approval.process.started"]`.
2. Вызов с `actorId`, не равным `process.getInitiatorId()`, — переход не выполняется
   (`TransitionResult.performed() == false`, `status` не меняется) — guard `IsInitiator`
   блокирует.
3. Процесс с обязательным (`mandatory = true`) этапом без участников в текущей итерации —
   переход не выполняется по той же причине (`AllMandatorySlotsFilled`).
4. Процесс с необязательным (`mandatory = false`) этапом без участников — переход
   **выполняется** (guard проверяет только обязательные этапы).
5. Повторный вызов `startProcess` на уже переведённом в `InProgress` процессе — либо
   `NoApplicableTransitionException` (нет перехода из `InProgress` по `StartProcess`/
   `USER_ACTION` для этого конфига — конфиг Фазы 3 определяет переход только из `Draft`),
   либо иное явное поведение — задокументировать выбор в PR (в духе решения открытого
   вопроса №1 Фазы 2, тот же принцип: явная ошибка конфигурации, не тихий no-op).

## Тестирование

- Юнит-тесты на guard-бины (`isInitiatorGuard`, `allMandatorySlotsFilledGuard`,
  `allDurationsValidGuard`) по отдельности — без БД, на сконструированных вручную
  `ProcessInstance`/`StageInstance`/`StageIteration`/`Participant` (через builder'ы,
  `@SuperBuilder` уже есть на всех этих сущностях).
- Интеграционный тест (Testcontainers, миграции `V1`–`V18` применяются как есть) —
  сохранить `ProcessInstance` со стадиями/итерациями/участниками напрямую через
  репозитории, вызвать `ProcessService.startProcess`, проверить критерии 1–5 через
  реальную БД (тот же паттерн, что `TransitionEngineIntegrationTest` в Фазе 2 — но здесь
  без отдельной тестовой сущности/тестовой миграции: используются настоящие
  `process_instance`/`stage_instance`/... и настоящая миграция `V18`, а не
  `db/test-migration`, потому что guard/action-бины и конфиг здесь — не тестовые заглушки,
  а часть продовой конфигурации).

## Открытые вопросы

1. Пакет для `ProcessRepository` (`service` vs `domain.process`) и для guard/action-бинов
   (`service.guard`/`service.action` vs что-то ещё) — решение принимает Claude Code,
   задокументировать в PR, чтобы Фаза 4 (StageService) могла следовать тому же соглашению.
2. Поведение при повторном `StartProcess` на уже `InProgress` процессе (критерий приёмки 5)
   — какое конкретно исключение/результат. Cowork сверит выбор при следующем ревью.
3. `AllMandatorySlotsFilled` в этой фазе — упрощённая (per-stage, не per-slot) реализация,
   явно помеченная как временная в `guard_registry.description` (см. миграцию). Когда
   появится `RouteGeneratorService`, потребуется либо расширить эту же guard-реализацию до
   буквальной per-slot проверки, либо осознанно оставить упрощённую версию — решение за
   пределами этой фазы, но тикет той фазы должен явно сослаться на эту заметку.
