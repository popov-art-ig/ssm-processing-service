# PHASE-02 — State Machine Engine (TransitionEngine и окружение)

> См. `docs/tickets/TEMPLATE.md` за описанием процесса. Этот тикет самодостаточен: весь
> материал, нужный для реализации, скопирован ниже прямо в текст. Пометки вида
> «(источник: `11_adr.md` ADR-028)» — это атрибуция для истории/ревью, не рабочие ссылки:
> у вас нет доступа к этим файлам, они существуют только в Claude Project и не
> экспортированы в репозиторий.
>
> Подготовлен по итогам первого прохода ревью Фазы 1 (PR #1, PR #2) и уточнения версии
> Gradle (8.8 → 8.14, см. коммит с исправлением сборки) — эта фаза само по себе версий
> Gradle/Boot/Cloud/Hibernate не касается.

## Статус

На проверке (PR открыт)

## Контекст

Фаза 1 подняла фундамент: Gradle-проект, доменные сущности всех 5 агрегатов, полная схема
БД (27 таблиц), базовый `application.yml`. Ни один класс из
`ru.coordination.approval.domain.statemachine` или `ru.coordination.approval.domain.registry`
пока не используется исполняемой логикой — это просто JPA-сущности над готовыми таблицами.

Фаза 2 — первая фаза, реализующая **поведение**: собственный движок машины состояний
(`TransitionEngine`). Он заменяет Spring State Machine (SSM), от которого разработчики
библиотеки официально отказались (архивирована 2026-07-05, без поддержки Spring Boot 4).
Модуль изначально проектировался так, что состояния и переходы — данные в БД, а не код
(«Data-Driven State Machine»), а guards/actions — заменяемые Spring-бины; поэтому замена
исполняющего движка не требует изменения модели конфигурации, только замены компонента,
который её исполняет (источник: `11_adr.md` ADR-028, ADR-002).

Эта фаза — ядро, от которого зависят все последующие фазы (guards/actions как конкретные
бины, REST API, Event Publisher, адаптеры, планировщик): без неё нет ни одного перехода
состояния, которым мог бы воспользоваться остальной код.

## Источники (для истории, не для перехода)

- `11_adr.md` ADR-028 (решение о собственном движке) и ADR-002 (Data-Driven State Machine).
- `04_state_machines.md` §3.2–3.4 (модель конфига, алгоритм перехода, версионирование).
- `10_architecture.md` §7 (компоненты State Machine Engine).
- `05_guards_actions_registry.md` (контракт guards/actions, реестр).
- `08_db_schema.md` §15–16, §5.1 (DDL).

## Объём фазы (Scope)

### 1. Модель конфигурации (для справки — уже реализована в Фазе 1)

Машина состояний описывается тремя таблицами / JPA-сущностями, уже существующими в
репозитории (см. раздел «Что уже есть» ниже) — эта фаза их не меняет, только читает.
Концептуально:

```
StateMachineConfig
├── entityType        // PROCESS | STAGE | STAGE_ITERATION | PARTICIPANT |
│                      // ADDITIONAL_APPROVER | FINAL_DECISION | REMARK
├── processType        // STANDARD | UNIFIED | null
├── version             // бизнес-номер версии
├── status              // Draft | Published | Deprecated | Archived
├── states[]            // StateConfig: code, displayName, isInitial, isTerminal
└── transitions[]       // TransitionConfig: code, fromState, toState, trigger,
                        //   guards[], actions[], emits[], priority, isActive
```

`TriggerType` — три значения: `USER_ACTION`, `SYSTEM_ACTION`, `TIMER` (в БД записаны как
`userAction`/`systemAction`/`timer` на уровне бизнес-описания, как enum
`ru.coordination.approval.domain.statemachine.TriggerType` в коде — см. существующий класс).

### 2. Алгоритм перехода — единственный источник истины по логике `TransitionEngine`

Дословно (источник: `04_state_machines.md` §3.3):

```
1. Найти переходы по (entityType, fromState, trigger, processType)
2. Отсортировать по priority
3. Для каждого:
   a. Проверить guards
   b. Если все true → actions, сменить state, опубликовать events
   c. AuditEvent
   d. Выход
4. Если ни один не сработал → ошибка/игнор
```

Расшифровка шагов для реализации:
- Шаг 1: фильтр `transition_config` по `config_id` (актуальная опубликованная версия
  `state_machine_config` для данных `entityType`+`processType`), `from_state = fromState`,
  `trigger = trigger`, `is_active = true`.
- Шаг 2: сортировка по `priority` (число, больший приоритет обрабатывается первым —
  порядок сортировки на усмотрение реализации, но должен быть детерминирован и
  задокументирован в PR).
- Шаг 3a: guards — предикаты без побочных эффектов, каждый возвращает `true`/`false`; если
  хотя бы один вернул `false` — этот переход пропускается, проверяется следующий по
  приоритету.
- Шаг 3b: actions — операции с побочными эффектами (могут изменять состояние сущностей,
  вызывать адаптеры, готовить публикацию событий), выполняются строго по одной, в порядке
  списка `actions[]` перехода; после всех actions — запись `to_state` в поле `status`
  целевой сущности.
- Шаг 3c: `AuditEvent` создаётся на каждый выполненный переход (обязательно, без
  исключений — см. `Сводные инварианты` ниже).
- Шаг 4: если циклом по шагу 3 не найден ни один переход, все guards которого вернули
  `true` (включая случай, когда подходящих по `(fromState, trigger)` переходов вообще нет)
  — это тот самый открытый вопрос (см. «Открытые вопросы» ниже): решение по конкретному
  поведению принимает Claude Code.

### 3. `ModelFactory` (`ru.coordination.approval.engine.model` — пакет на усмотрение Claude
   Code, но в новом top-level пакете `engine`, отдельном от `domain`)

- Загружает `StateMachineConfig` (со связанными `StateConfig`/`TransitionConfig`) —
  по конкретному id версии конфига, переданному вызывающим кодом явно (см. ниже про
  Snapshot-on-Start — сама фиксация версии на процессе не входит в эту фазу).
- Строит in-memory модель переходов, пригодную для быстрого поиска по
  `(fromState, trigger)` внутри одной загруженной версии конфига.
- Не кэширует конфиг бессрочно между вызовами — конфиг может быть опубликован заново
  (новая версия с новым id), старые процессы должны продолжать работать по версии,
  зафиксированной на старте (принцип Snapshot-on-Start, источник: `04_state_machines.md`
  §2.6, §3.4). В этой фазе `ModelFactory` достаточно уметь загружать модель **по
  конкретному id версии конфига** — механизм фиксации версии на самом процессе
  (`ProcessInstance`/аналоги) реализуется в фазе доменных сервисов (Domain Core), не здесь.

### 4. `GuardRegistry` и `ActionRegistry` (`ru.coordination.approval.engine.registry`)

Контракт guard/action (источник: `05_guards_actions_registry.md` §2.1–2.2):

- **Guard** — предикат: принимает контекст, возвращает `boolean`; не имеет побочных
  эффектов; может принимать параметры.
- **Action** — операция: принимает контекст, не возвращает значение; может изменять
  состояние, вызывать внешние системы (через адаптеры — не в объёме этой фазы), публиковать
  события (в этой фазе — только возвращать код события в `emits`, не публиковать
  физически).
- **Context** — данные, доступные guard/action во время выполнения перехода: сущность,
  текущий пользователь, параметры перехода, доступ к внешним сервисам. Точный Java-тип
  контекста не зафиксирован ни одним документом — проектируется в этой фазе (см. «Открытые
  вопросы»).

Эти интерфейсы (`Guard`, `Action`) в коде пока не существуют — их нужно создать в этой
фазе.

Реестры (`GuardRegistry`/`ActionRegistry`) резолвят код guard/action
(`transition_config.guards[]`/`actions[]`) в исполняемый Spring-бин через
`guard_registry.handler`/`action_registry.handler` (полное имя бина/класса — поле уже есть
в существующих сущностях `GuardRegistryEntry`/`ActionRegistryEntry`, см. «Что уже есть»).
Ищут бины через `ComponentResolver` (ниже), не обращаются к Spring `ApplicationContext`
напрямую из `TransitionEngine`.

Ограничения на guards/actions (источник: `05_guards_actions_registry.md` §8.3, применимо и
к встроенным, не только к CUSTOM):
- Guards не могут менять состояние напрямую.
- Guards/actions должны быть идемпотентны.
- Guards/actions должны работать в рамках той же транзакции, что и `TransitionEngine`.

**Не входит в эту фазу:** сами конкретные guard/action-бины бизнес-логики (например,
`IsInitiator`, `AllMandatorySlotsFilled`, `AssignStageTasks` — полный список в
`05_guards_actions_registry.md` §4–5, порядка 35 guards и 40 actions) — только механизм
резолвинга. Для тестирования этой фазы достаточно 1–2 тестовых guard/action-заглушек.

### 5. `ComponentResolver` (`ru.coordination.approval.engine`)

Тонкая обёртка над `ApplicationContext`/`GuardRegistry`/`ActionRegistry`, разрешающая код
перехода в конкретные бины для `TransitionEngine`.

### 6. `TransitionEngine` (`ru.coordination.approval.engine`)

- Публичный контракт, минимально: метод вида
  `TransitionResult transition(EntityType entityType, UUID entityId, String currentState, TriggerType trigger, TransitionContext context)`
  (точную сигнатуру, включая тип `TransitionContext`, определяет Claude Code).
- Реализует алгоритм из раздела 2 выше дословно.
- Пишет новое значение `status` **непосредственно в сущность** (`process_instance.status`,
  `stage_instance.status` и т.д.) в той же транзакции, что и actions и `AuditEvent`.
  Отдельной таблицы персистентности контекста нет и не создаётся (источник: `11_adr.md`
  ADR-028 — движок не хранит сериализованный `extended_state`/`state_machine_context`,
  в отличие от прежнего Spring State Machine).
- Валидирует, что `toState` присутствует в `status_registry` для данного `entityType`
  (см. DDL `status_registry` ниже) — защита от рассинхронизации конфига и реестра
  статусов, а не дублирование источника истины.
- Использует оптимистичную блокировку через поле `version` (JPA `@Version`) изменяемой
  сущности — при конфликте (`OptimisticLockException`) не глотать исключение молча,
  передавать вызывающему коду (стратегия retry — не в объёме этой фазы).
- **Не пишет** и не читает никакую сущность из пакета `domain.statemachine` кроме
  `StateMachineConfig`/`StateConfig`/`TransitionConfig` (через `ModelFactory`) — конкретные
  `process_instance`/`stage_instance`/... передаются вызывающим кодом как обобщённая
  JPA-сущность или через минимальный интерфейс (например, `HasStatus`/`Versioned`) —
  решение по механизму принимает Claude Code, задокументировав его в PR.

## Явно не входит (Out of scope)

- Конкретные guard/action-бины бизнес-логики любого домена — появляются по мере реализации
  соответствующих доменных фаз и сами регистрируют себя в `guard_registry`/`action_registry`.
- `ProcessService`/`StageService`/другие Domain Core сервисы, вызывающие `TransitionEngine` —
  следующая фаза.
- Публикация событий в Outbox/RabbitMQ — `TransitionEngine` в этой фазе только возвращает
  список кодов событий из `emits[]`.
- REST API.
- Snapshot-on-Start/Fork & Drain как поведение `ProcessService` (фиксация версии конфига на
  процессе) — в объёме этой фазы только то, что `ModelFactory` умеет строить модель по
  конкретной, явно переданной версии конфига.
- Наполнение `guard_registry`/`action_registry` реальными строками сверх тестовых заглушек.

## Что уже есть в репозитории на момент постановки

Готово и не требует изменений (Фаза 1) — все пути ниже реальны в этом репозитории:

- `src/main/java/ru/coordination/approval/domain/statemachine/StateMachineConfig.java`,
  `StateConfig.java`, `TransitionConfig.java`, `TriggerType.java` — JPA-сущности над
  `state_machine_config`/`state_config`/`transition_config`.
- `src/main/java/ru/coordination/approval/domain/registry/GuardRegistryEntry.java`,
  `ActionRegistryEntry.java` — JPA-сущности над `guard_registry`/`action_registry`, поля
  `code`, `handler`, `scope`, `categories`, `applicableEntities`, `active`.
- `src/main/java/ru/coordination/approval/domain/registry/StatusRegistry.java` — JPA-сущность
  над `status_registry`, поле `code` уникально в рамках `entityType`. Внимание:
  `StatusRegistry.entityType` — `String`, а не enum
  `ru.coordination.approval.domain.common.EntityType`, как в `StateMachineConfig`; при
  сверке использовать `.name()`/эквивалент — так задано схемой БД, это не ошибка Фазы 1.
- Все сущности пакета `domain/process/` (`ProcessInstance`, `StageInstance`,
  `StageIteration`, `Participant`, `AdditionalApprover`, `Decision`, `FinalDecision`,
  `Remark`, `Comment`) уже имеют поле `status: String` (не enum) и `@Version`-поле
  оптимистичной блокировки — готовы к использованию `TransitionEngine` без изменений.
- Репозиториев (Spring Data JPA) пока нет ни для одной сущности — если `ModelFactory` или
  тесты этой фазы нуждаются в `StateMachineConfigRepository`, его нужно создать в рамках
  этой фазы.

### DDL трёх таблиц конфигурации (справочно, схема уже применена миграциями Фазы 1 —
`src/main/resources/db/migration/V11__state_machine_config.sql`)

```sql
create table state_machine_config (
    id                      uuid primary key,
    version                 integer not null,
    entity_type             varchar(50) not null
                            check (entity_type in ('PROCESS', 'STAGE', 'STAGE_ITERATION', 'PARTICIPANT', 'ADDITIONAL_APPROVER', 'FINAL_DECISION', 'REMARK')),
    process_type            varchar(50) null,
    status                  varchar(50) not null
                            check (status in ('DRAFT', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED')),
    active_process_count    integer not null default 0,
    created_at              timestamptz not null default now(),
    created_by              uuid not null,
    published_at            timestamptz null,
    published_by            uuid null,
    updated_at              timestamptz not null default now(),
    version_lock            integer not null default 0
);
create unique index ux_state_machine_config_version
    on state_machine_config(entity_type, process_type, version);
create index ix_state_machine_config_status on state_machine_config(status);

create table state_config (
    id                      uuid primary key,
    config_id               uuid not null references state_machine_config(id),
    code                    varchar(100) not null,
    display_name            varchar(255) not null,
    is_initial              boolean not null default false,
    is_terminal             boolean not null default false,
    metadata                jsonb not null default '{}',
    created_at              timestamptz not null default now()
);
create unique index ux_state_config_code on state_config(config_id, code);

create table transition_config (
    id                      uuid primary key,
    config_id               uuid not null references state_machine_config(id),
    code                    varchar(100) not null,
    from_state              varchar(100) not null,
    to_state                varchar(100) not null,
    trigger                 varchar(50) not null
                            check (trigger in ('USER_ACTION', 'SYSTEM_ACTION', 'TIMER')),
    guards                  text[] not null default '{}',
    actions                 text[] not null default '{}',
    emits                   text[] not null default '{}',
    priority                integer not null default 0,
    is_active               boolean not null default true,
    created_at              timestamptz not null default now()
);
create unique index ux_transition_config_code on transition_config(config_id, code);
create index ix_transition_config_from on transition_config(config_id, from_state);
create index ix_transition_config_trigger on transition_config(config_id, trigger);
```

### DDL `status_registry` (справочно, уже применена — `V1__registries.sql`)

```sql
create table status_registry (
    code                    varchar(100) primary key,
    entity_type             varchar(50) not null,
    display_name            varchar(255) not null,
    description             text null,
    is_terminal             boolean not null default false,
    category                varchar(50) null,
    metadata                jsonb not null default '{}',
    is_active               boolean not null default true,
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now()
);
create index ix_status_registry_entity on status_registry(entity_type);
```

## Технические требования

- Новый top-level пакет `ru.coordination.approval.engine` (и подпакеты `engine.model`,
  `engine.registry` по необходимости) — не смешивать с `domain`.
- Guard/Action — интерфейсы, реализации резолвятся как Spring-бины по имени/типу, а не
  вручную через `if/else` по коду.
- Никакой сериализации состояния (JSON-контекст, снапшоты) — противоречит принятому решению
  об отказе от отдельной таблицы персистентности контекста.
- Тестовые guard/action-заглушки для интеграционных тестов должны быть явно помечены как
  тестовые (например, `@Profile("test")` или отдельный тестовый пакет), чтобы не попасть в
  прод-конфигурацию `guard_registry`/`action_registry` по ошибке.

## Критерии приёмки

1. Для сконфигурированного вручную (в тесте) `StateMachineConfig` с 2+ переходами из одного
   `fromState` по одному `trigger`, но с разными `priority` и guard-условиями,
   `TransitionEngine` выбирает первый по `priority` переход, чьи guards все вернули `true`,
   и пропускает более приоритетные переходы, чьи guards вернули `false`.
2. При успешном переходе: `status` сущности меняется на `toState`; все `actions` перехода
   выполнены ровно один раз, в порядке, определённом в конфиге; возвращён список кодов из
   `emits[]`; создана запись `AuditEvent`, отражающая переход.
3. Если ни один переход не найден или все guards вернули `false` для всех подходящих
   переходов — метод ведёт себя согласно решению, зафиксированному в PR по открытому
   вопросу ниже (бросает конкретное исключение либо возвращает явный «не выполнено»-
   результат) — но не молчит и не оставляет `status` в неопределённом состоянии.
4. `toState`, отсутствующий в `status_registry` для данного `entityType`, приводит к ошибке
   конфигурации, а не к тихой записи невалидного статуса.
5. Конкурентная попытка перехода над одной и той же сущностью (эмулированная в тесте через
   ручное расхождение `version`) приводит к `OptimisticLockException`, не к потерянному
   обновлению.
6. `ModelFactory` корректно строит модель для `StateMachineConfig` с несколькими версиями
   одного `(entityType, processType)` — обращаясь по id конкретной версии, а не всегда беря
   «последнюю»/«опубликованную» неявно.
7. Все новые классы находятся в пакете `ru.coordination.approval.engine` (или подпакетах),
   без циклических зависимостей с `domain`.

## Тестирование

- Юнит-тесты на `TransitionEngine` с in-memory/мок-моделью (не требуют БД) — покрывают
  критерии 1–4 выше через сконструированные вручную `TransitionConfig`.
- Интеграционный тест (Testcontainers, уже настроен в Фазе 1 —
  `src/main/resources/application.yml` профиль `test`, `jdbc:tc:postgresql:16-alpine`) на
  полном цикле: сохранённый в БД `StateMachineConfig` → `ModelFactory` →
  `TransitionEngine.transition(...)` → сущность в БД имеет новый `status` и создан
  `AuditEvent`. Использовать тестовые guard/action-заглушки, не бизнес-логику.
- Тест на оптимистичную блокировку (критерий 5).
- Явных сценариев под именно эту фазу в спецификации нет (бизнес-сценарии описаны на уровне
  доменных операций, а не движка как отдельного модуля) — ориентироваться на критерии
  приёмки выше.

## Открытые вопросы

1. Поведение при отсутствии подходящего перехода (все guards `false`, либо переходов с
   таким `(fromState, trigger)` нет вообще) — алгоритм говорит «ошибка/игнор», не уточняя,
   что именно. Решение принимает Claude Code (например: бросать
   `NoApplicableTransitionException` для явного отсутствия конфигурации и молча
   возвращать «не выполнено» для непройденных guards — это два разных по природе случая),
   с обязательным явным обоснованием в описании PR — Cowork сверит это решение со спекой
   при следующем ревью и, если оно расходится с намерением исходного алгоритма, зафиксирует
   уточнение как addendum к ADR-028.
2. Точная сигнатура `TransitionContext` (что передаётся guard/action — сама сущность,
   параметры триггера, ссылка на инициатора и т.п.) не зафиксирована ни в одном документе
   на уровне кода. Claude Code проектирует минимально достаточный контракт для этой фазы
   (guard/action-заглушек) — он неизбежно будет расширяться в фазах, реализующих конкретные
   guards/actions, и это ожидаемо, а не повод блокироваться сейчас.
