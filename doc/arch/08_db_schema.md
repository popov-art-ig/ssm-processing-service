# Схема базы данных

**Версия:** 2.0  
**Назначение:** полное описание структуры базы данных модуля «Согласование»

---

## Оглавление

1. Назначение и обоснование
2. Принципы проектирования
3. Соглашения
4. Общая схема по группам
5. Группа 1: Справочники
6. Группа 2: Шаблоны маршрутов
7. Группа 3: Процессы
8. Группа 4: Этапы и итерации
9. Группа 5: Участники
10. Группа 6: Дополнительные согласующие
11. Группа 7: Решения
12. Группа 8: Замечания и комментарии
13. Группа 9: Финальное решение
14. Группа 10: Архивация
15. Группа 11: Конфигурация state machine
16. Группа 12: Реестр guards и actions
17. Группа 13: (упразднена, ADR-028) — ранее Spring State Machine, персистентность
18. Группа 14: Shedlock
19. Группа 15: Outbox
20. Группа 16: Идемпотентность
21. Группа 17: Аудит
22. Группа 18: Настройки уведомлений
23. Индексы
24. Ограничения
25. Партиционирование
26. Read-модели для агрегированных эндпоинтов
27. Миграции
28. Сводная таблица

---

## 1. Назначение и обоснование

| Что | Зачем |
|---|---|
| Реестры | Гибкость без миграций |
| Партиционирование | Работа с большими объёмами |
| Read-модели | Быстрые агрегаты для Front |

---

## 2. Принципы

### 2.1 Нормализация

- Схема нормализована до **3NF**.
- Дублирование — только в read-моделях.

### 2.2 Идентификаторы

- Все PK — **UUID v4**.
- UUID генерируется приложением.
- Порядковые номера (`order_idx`) — отдельные поля, не PK.

### 2.3 Гибкость через справочники

**Принцип.** Статусы и результаты решений хранятся как **справочники (реестры)**, а не `CHECK`-констрейнты.

**Обоснование.** Добавление нового статуса не требует миграции БД.

**Исключение.** Фиксированные значения (статусы шаблонов, статусы конфигов) — через `CHECK`.

### 2.4 Версионирование

- Шаблоны и конфиги **версионируются**.
- Snapshot-on-Start — версия фиксируется в процессе.
- Fork & Drain — старые версии живут, пока есть активные процессы.
- **Шаблоны версионируются одной таблицей `template`** через цепочку `parent_template_id` (ADR-023) — по аналогии со `state_machine_config`. Отдельной таблицы версий у шаблонов, в отличие от версии 1.1, больше нет.

### 2.5 Мягкое удаление

- Шаблоны и конфиги **не удаляются** — только меняют статус.
- Процессы **не удаляются** — архивируются.
- Замечания и комментарии **не удаляются** — меняют статус.

### 2.6 Аудит

- Каждое изменение фиксируется в отдельной таблице.
- Аудит — append-only.

### 2.7 Единый механизм итераций

**Ключевая идея.** Итерации — **только на уровне этапов**. «Итерация процесса» — вычисляемое представление (`max(stageIteration)`), не хранится физически.

---

## 3. Соглашения

### 3.1 Именование таблиц

**Формат:** `<domain>_<entity>` в `snake_case`.

### 3.2 Именование полей

- `snake_case`.
- Первичный ключ — `id`.
- Внешний ключ — `<entity>_id`.
- Булевы — `is_<something>`.
- Даты — `<action>_at`.
- Длительности — `<something>_days`.

### 3.3 Типы данных

| Логический тип | PostgreSQL |
|---|---|
| UUID | `uuid` |
| Строка | `varchar(n)` |
| Текст | `text` |
| Целое | `integer` / `bigint` |
| Boolean | `boolean` |
| Enum | `varchar` (+ check или реестр) |
| Дата/время | `timestamptz` |
| JSON | `jsonb` |
| Массив UUID | `uuid[]` |
| Массив Int | `integer[]` |

### 3.4 Стандартные поля

| Поле | Тип | Описание |
|---|---|---|
| `id` | `uuid` | Первичный ключ |
| `created_at` | `timestamptz` | Дата создания |
| `updated_at` | `timestamptz` | Дата обновления |

### 3.5 Версионирование записей

Поле `version` (integer) для оптимистичной блокировки.

---

## 4. Общая схема по группам

| Группа | Таблицы | Описание |
|---|---|---|
| 1. Справочники | `status_registry`, `decision_result_registry` | Изменяемые значения |
| 2. Шаблоны | `template`, `stage_template`, `slot_template`, `applicability_rule` | Шаблоны маршрутов |
| 3. Процессы | `process_instance` | Экземпляры процессов |
| 4. Этапы и итерации | `stage_instance`, `stage_iteration` | Структура маршрута |
| 5. Участники | `participant` | Согласующие, подписанты |
| 6. Доп. согласующие | `additional_approver` | Иерархия доп. согласующих |
| 7. Решения | `decision` | Решения участников |
| 8. Замечания и комментарии | `remark`, `comment`| Обсуждение |
| 9. Финальное решение | `final_decision` | Финальное решение |
| 10. Архивация | `archive_metadata` | Метаданные архивации |
| 11. Конфигурация SSM | `state_machine_config`, `state_config`, `transition_config` | Карты переходов |
| 12. Реестр guards/actions | `guard_registry`, `action_registry` | Реестр условий и действий |
| 13. (упразднена, ADR-028) | — | Ранее — персистентность Spring State Machine; движок заменён, отдельная таблица не требуется (см. §17) |
| 14. Shedlock | `shedlock` | Распределённые блокировки |
| 15. Outbox | `outbox_event` | Надёжная публикация событий |
| 16. Идемпотентность | `idempotency_key` | Идемпотентность API |
| 17. Аудит | `audit_event`, `config_audit_event` | Аудит изменений |
| 18. Настройки уведомлений | `notification_settings` | Настраиваемая периодичность напоминаний (ADR-027) |

**Изменения от версии 1.1 (ADR-023, ADR-026):**
- Таблица `template_version` **убрана**. `stages` (`stage_template`), `applicability_rule` и поля `responsible_roles`/`requires_responsible_approval` перенесены непосредственно на `template`; версионирование — через `parent_template_id` (см. §6).
- Таблицы `remark_attachment` и `comment_attachment`, ранее упоминавшиеся в сводной таблице без описания, **убраны полностью** — вложения хранятся как `attachments uuid[]` непосредственно в `remark`/`comment` (см. §12), без отдельных таблиц.
- Таблицы `shedlock` (группа 14) и `idempotency_key` (группа 16), ранее упоминавшиеся в сводной таблице без DDL, **описаны** в §18 и §20.

**Изменения от 2026-09-18 (ADR-027):** добавлена группа 18 — `notification_settings` (singleton, настраиваемая администратором периодичность напоминаний). Закрывает открытый пробел из `claude/gap-analysis-business-context-vs-architecture.md` («Напоминания»).

**Изменения от 2026-09-18 (ADR-028):** движок машины состояний заменён собственной реализацией (Spring State Machine архивирован разработчиками и не поддерживает Spring Boot 4, на который поднимается проект). Таблица `ssm_state_machine_context` (ранее группа 13) **удалена из схемы** — текущее состояние сущности хранится непосредственно в её собственном поле `status`, уже валидируемом через `status_registry` (ADR-015); отдельная таблица для сериализованного контекста движка не нужна. Номер группы 13 сохранён как исторический (не переиспользуется), чтобы не сдвигать нумерацию последующих групп/секций документа — см. §17.

---

## 5. Группа 1: Справочники

### 5.1 `status_registry` — Реестр статусов

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
create index ix_status_registry_active on status_registry(is_active);
```

**Обоснование:**
- Хранит все возможные статусы для всех сущностей.
- Добавление нового статуса — INSERT, без миграции.
- `entity_type` — для какой сущности. Значения соответствуют `state_machine_config.entity_type` (§15.1): `PROCESS`, `STAGE`, `STAGE_ITERATION`, `PARTICIPANT`, `ADDITIONAL_APPROVER`, `FINAL_DECISION`, `REMARK`.
- `is_terminal` — финальный ли статус.
- **С ADR-028** `status` каждой сущности, валидируемый через этот реестр, — единственное хранимое представление текущего состояния машины состояний (ранее оно дублировалось в `ssm_state_machine_context.state`).

**Пример данных:**

| code | entity_type | display_name |
|---|---|---|
| DRAFT | PROCESS | Черновик |
| IN_PROGRESS | PROCESS | В процессе |
| ON_REWORK | PROCESS | На доработке |
| APPROVED | PROCESS | Согласован |
| ARCHIVED | PROCESS | Архивирован |
| PENDING | STAGE | Ожидает |
| ACTIVE | STAGE | Активен |
| PROPOSED | REMARK | Предложено |

### 5.2 `decision_result_registry` — Реестр результатов решений

```sql
create table decision_result_registry (
    code                    varchar(100) primary key,
    display_name            varchar(255) not null,
    description             text null,
    is_positive             boolean not null default false,
    is_negative             boolean not null default false,
    is_terminal             boolean not null default true,
    metadata                jsonb not null default '{}',
    is_active               boolean not null default true,
    created_at              timestamptz not null default now()
);
```

**Пример данных:**

| code | display_name | is_positive | is_negative |
|---|---|---|---|
| APPROVE | Согласовать | true | false |
| APPROVE_WITH_COMMENTS | Согласовать с замечаниями | true | false |
| REJECT | Отклонить | false | true |

**ABSTAIN убран по требованию.**

---

## 6. Группа 2: Шаблоны маршрутов

### 6.1 `template` — Шаблон

```sql
create table template (
    id                            uuid primary key,
    name                          varchar(255) not null,
    process_type                  varchar(50) not null 
                                  check (process_type in ('STANDARD', 'UNIFIED')),
    status                        varchar(50) not null 
                                  check (status in ('DRAFT', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED')),
    version                       integer not null default 1,
    parent_template_id            uuid null references template(id),
    process_iteration_enabled     boolean not null default false,
    responsible_roles             uuid[] null,
    requires_responsible_approval boolean not null default false,
    created_at                    timestamptz not null default now(),
    created_by                    uuid not null,
    updated_at                    timestamptz not null default now(),
    updated_by                    uuid null,
    published_at                  timestamptz null,
    published_by                  uuid null
);

create unique index ux_template_name_version 
    on template(name, version) 
    where status in ('DRAFT', 'PUBLISHED');

create index ix_template_status on template(status);
create index ix_template_process_type on template(process_type);
create index ix_template_parent on template(parent_template_id);
```

**Убрано:** `max_process_iterations`.

**Добавлено (ADR-023):** `responsible_roles`, `requires_responsible_approval` — перенесены из удалённой `template_version` (см. §6.2 — историческое примечание ниже).

**Семантика `processIterationEnabled`:**

| Значение | Что разрешено |
|---|---|
| false | Только правки в рамках одного документа |
| true | Ревизии документа (новые процессы в агрегате) |

### 6.2 Версионирование `template` (ADR-023)

**Историческое примечание.** В версии 1.1 существовала отдельная таблица `template_version` (`id`, `template_id`, `version`, `process_type`, `responsible_roles`, `requires_responsible_approval`), а `stage_template`/`applicability_rule` ссылались на неё, а не на `template`. Это создавало двойную модель версионирования: и `template.version` / `template.parent_template_id`, и параллельно `template_version.version`. С ADR-023 таблица `template_version` **удалена**. Версионирование шаблона происходит **только** через `template.parent_template_id` — точно так же, как уже было описано для форка (`POST /templates/{id}/fork`, см. `07_api_contract.md` §10.6), и по аналогии с тем, как версионируется `state_machine_config` (одна таблица, счётчик `version`, без отдельной таблицы версий).

**Как это работает:**
- Публикация новой версии шаблона = создание новой строки `template` с новым `id`, `version = предыдущая + 1`, `parent_template_id = id предыдущей версии`, `stages`/`applicability_rule` создаются заново для новой строки (не копируются как есть — правило "только additive" всё равно требует создания новых строк `stage_template`, так как `stage_template.template_id` ссылается на конкретную версию).
- `process_instance.template_ref` указывает на конкретную строку `template` (конкретную версию) — этого достаточно для Snapshot-on-Start; отдельного столбца `template_version` в `process_instance` не требуется (см. §7.1).
- Fork & Drain работает как прежде: старая версия `template` не удаляется, пока есть процессы, ссылающиеся на неё через `template_ref`.

### 6.3 `stage_template` — Этап шаблона

```sql
create table stage_template (
    id                      uuid primary key,
    template_id             uuid not null references template(id),
    order_idx               integer not null,
    name                    varchar(255) null,
    description             text null,
    stage_type              varchar(50) not null 
                            check (stage_type in ('APPROVAL', 'SIGNING', 'ENDORSEMENT')),
    duration                integer null check (duration > 0),
    decision_mode           varchar(50) null 
                            check (decision_mode in ('AND', 'ANY_APPROVE', 'ANY_REJECT', 'ANY_DECISION', 'FIRST_REJECT_FAIL_FAST')),
    execution_order         varchar(50) null 
                            check (execution_order in ('PARALLEL', 'SEQUENTIAL')),
    is_mandatory            boolean not null default false,
    is_order_mandatory      boolean not null default false,
    allowed_return_stages   integer[] null,
    created_at              timestamptz not null default now()
);

create index ix_stage_template_template on stage_template(template_id);
create unique index ux_stage_template_order 
    on stage_template(template_id, order_idx);
```

**Изменено (ADR-023):** `template_version_id` → `template_id` (ссылается непосредственно на `template`, так как `template_version` удалена).

**Добавлено:** `allowed_return_stages`.

**Реализация `decision_mode`/`execution_order` (ADR-022).** Оба поля реализованы в guards/actions — см. `05_guards_actions_registry.md` §4.2, §5.2 и `04_state_machines.md` §5.2, §7.

**Семантика `allowed_return_stages`:**

| Значение | Что значит |
|---|---|
| null или `{}` | Возврат только на текущий этап |
| `{1, 2}` | Возврат на этап 1 или 2 |

**Правила:**
- Только прошлые и текущий этапы (≤ `order_idx`).
- Без дублей.
- Работает всегда, независимо от `processIterationEnabled`.

### 6.4 `slot_template` — Унифицированный слот

```sql
create table slot_template (
    id                          uuid primary key,
    parent_slot_id              uuid null references slot_template(id),
    stage_template_id           uuid null references stage_template(id),
    slot_type                   varchar(50) not null 
                                check (slot_type in ('ACTOR', 'ADDITIONAL_APPROVER')),
    order_idx                   integer not null,
    user_id                     uuid null,
    organization_id             uuid null,
    acceptable_roles            uuid[] not null default '{}',
    required                    boolean not null default true,
    is_user_editable            boolean not null default true,
    is_organization_editable    boolean not null default false,
    is_deletable                boolean not null default false,
    due_offset                  varchar(10) null 
                                check (due_offset in ('H0', 'H3', 'H8')),
    created_at                  timestamptz not null default now(),
    
    check (is_organization_editable = false or is_user_editable = true),
    check (
        (slot_type = 'ACTOR' 
         and parent_slot_id is null 
         and stage_template_id is not null) 
        or
        (slot_type = 'ADDITIONAL_APPROVER' 
         and parent_slot_id is not null 
         and stage_template_id is null)
    )
);

create index ix_slot_template_parent on slot_template(parent_slot_id);
create index ix_slot_template_stage on slot_template(stage_template_id);
create index ix_slot_template_type on slot_template(slot_type);
```

**Обоснование:**
- Одна таблица для основных и доп. согласующих.
- Различие — только в `slot_type` и `due_offset`.
- Меньше кода, единая логика.

### 6.5 `applicability_rule` — Правило применимости

```sql
create table applicability_rule (
    id                      uuid primary key,
    template_id             uuid not null references template(id),
    rule_idx                integer not null,
    entity_types            text[] not null default '{}',
    entity_subtypes         text[] not null default '{}',
    attribute_conditions    jsonb not null default '{}',
    created_at              timestamptz not null default now()
);

create index ix_applicability_rule_template on applicability_rule(template_id);
```

**Изменено (ADR-023):** `template_version_id` → `template_id`.

---

## 7. Группа 3: Процессы

### 7.1 `process_instance` — Экземпляр процесса

```sql
create table process_instance (
    id                                  uuid primary key,
    entity_type                         varchar(100) not null,
    entity_subtype                      varchar(100) null,
    entity_id                           uuid not null,
    document_aggregate_id               uuid null,
    revision_label                      varchar(100) null,
    parent_process_id                   uuid null references process_instance(id),
    template_ref                        uuid not null references template(id),
    process_type                        varchar(50) not null,
    config_version                      integer not null,
    status                              varchar(100) not null,
    initiator_id                        uuid not null,
    responsible_user_id                 uuid null,
    responsible_resolved_role_ref       uuid null,
    created_at                          timestamptz not null default now(),
    started_at                          timestamptz null,
    completed_at                        timestamptz null,
    archived_at                         timestamptz null,
    auto_archive_scheduled_at           timestamptz null,
    version                             integer not null default 0
);

create index ix_process_entity on process_instance(entity_type, entity_subtype, entity_id);
create index ix_process_status on process_instance(status);
create index ix_process_initiator on process_instance(initiator_id);
create index ix_process_responsible on process_instance(responsible_user_id) 
    where responsible_user_id is not null;
create index ix_process_aggregate on process_instance(document_aggregate_id) 
    where document_aggregate_id is not null;
create index ix_process_parent on process_instance(parent_process_id) 
    where parent_process_id is not null;
create unique index ux_process_revision 
    on process_instance(document_aggregate_id, revision_label) 
    where document_aggregate_id is not null;
create index ix_process_auto_archive on process_instance(auto_archive_scheduled_at) 
    where status in ('APPROVED', 'APPROVED_WITH_COMMENTS', 'REJECTED', 'RECALLED');
```

**Изменения от версии 1.1:**
- **Убрано:** `currentProcessIteration`, `currentProcessIterationStartedAt`.
- **Убрано (ADR-023):** `template_version` — с удалением таблицы `template_version`, `template_ref` сам по себе однозначно указывает на зафиксированную версию шаблона (Snapshot-on-Start не требует отдельного номера).
- **Добавлено:** `document_aggregate_id`, `revision_label`, `parent_process_id`.
- Статус — `varchar(100)`, без check.
- **С ADR-028:** поле `status` — единственное хранимое представление текущего состояния процесса (ранее дублировалось в `ssm_state_machine_context.state`, привязанной к `machine_id = process_instance.id`); отдельная таблица контекста движка не используется.

**Семантика `revision_label`:** свободная строка (Изм. 1, Доп. 2, ТР. 3, Зам. 4). Модуль не проверяет формат.

---

## 8. Группа 4: Этапы и итерации

### 8.1 `stage_instance` — Этап

```sql
create table stage_instance (
    id                      uuid primary key,
    process_id              uuid not null references process_instance(id),
    order_idx               integer not null,
    original_order_idx      integer not null,
    name                    varchar(255) null,
    description             text null,
    stage_type              varchar(50) not null,
    duration                integer not null check (duration > 0),
    decision_mode           varchar(50) null,
    execution_order         varchar(50) null,
    is_mandatory            boolean not null default false,
    is_order_mandatory      boolean not null default false,
    allowed_return_stages   integer[] null,
    status                  varchar(100) not null,
    started_at              timestamptz null,
    due_at                  timestamptz null,
    completed_at            timestamptz null,
    created_at              timestamptz not null default now()
);

create index ix_stage_instance_process on stage_instance(process_id);
create unique index ux_stage_instance_order 
    on stage_instance(process_id, order_idx);
create index ix_stage_instance_status on stage_instance(status);
create index ix_stage_instance_due on stage_instance(due_at) 
    where status = 'ACTIVE';
```

**Изменения от версии 1.1:**
- **Убрано:** `process_iteration_id`.
- **Добавлено:** `process_id` (напрямую), `description`, `allowed_return_stages`.

### 8.2 `stage_iteration` — Итерация этапа

```sql
create table stage_iteration (
    id                  uuid primary key,
    stage_id            uuid not null references stage_instance(id),
    iteration_idx       integer not null,
    status              varchar(100) not null,
    started_at          timestamptz not null default now(),
    completed_at        timestamptz null,
    created_at          timestamptz not null default now()
);

create unique index ux_stage_iteration on stage_iteration(stage_id, iteration_idx);
create index ix_stage_iteration_status on stage_iteration(status);
```

**Убрано:** `process_iteration` как отдельная сущность.

**Ключевое правило:** при активации этапа, если у него уже есть итерации — создаётся новая.

### 8.3 Вычисляемое представление «итерация процесса»

**Не хранится физически.**

**Формула:**
```sql
select max(si.iteration_idx) as process_iteration
from stage_iteration si
join stage_instance s on s.id = si.stage_id
where s.process_id = :processId;
```

---

## 9. Группа 5: Участники

### 9.1 `participant` — Участник

```sql
create table participant (
    id                          uuid primary key,
    stage_iteration_id          uuid not null references stage_iteration(id),
    user_id                     uuid not null,
    organization_id             uuid null,
    actor_slot_ref               uuid null references slot_template(id),
    resolved_role_ref           uuid null,
    order_idx                   integer not null default 0,
    role                        varchar(50) not null 
                                check (role in ('APPROVER', 'SIGNER', 'ADDITIONAL_APPROVER', 'OBSERVER')),
    status                      varchar(100) not null,
    decision                    varchar(100) null,
    is_user_editable            boolean not null default true,
    is_organization_editable    boolean not null default false,
    is_deletable                boolean not null default false,
    assigned_at                 timestamptz null,
    due_at                      timestamptz null,
    decided_at                  timestamptz null,
    created_at                  timestamptz not null default now(),
    updated_at                  timestamptz not null default now()
);

create unique index ux_participant_iteration_user 
    on participant(stage_iteration_id, user_id, role);
create index ix_participant_stage_iteration on participant(stage_iteration_id);
create index ix_participant_user on participant(user_id);
create index ix_participant_status on participant(status);
create index ix_participant_due on participant(due_at) 
    where status in ('ASSIGNED', 'IN_PROGRESS');
create index ix_participant_order 
    on participant(stage_iteration_id, order_idx);
```

**Изменения:**
- **Убрано:** `substitution_ref`.
- **Добавлено (ADR-022):** `order_idx` — порядок работы участника на этапе при `execution_order = SEQUENTIAL` (см. `04_state_machines.md` §7, `05_guards_actions_registry.md` A-S-001/A-S-009). При `execution_order = PARALLEL` поле не используется для назначения задач.
- **Изменено (Fix 7.3):** `actor_slot_ref` теперь ссылается на `slot_template(id)` (было — `uuid null` без внешнего ключа).
- `assigned_at` теперь `null`-able — при `execution_order = SEQUENTIAL` участник в статусе `PENDING` ещё не получил задачу и `assigned_at` не заполнен.
- `role` — оставлен check (не registry).
- `status`, `decision` — без check (через реестры). Значения `status`, помимо ранее описанных, теперь включают `PENDING` (см. `04_state_machines.md` §7.1, ADR-022).

---

## 10. Группа 6: Дополнительные согласующие

### 10.1 `additional_approver` — Доп. согласующий

```sql
create table additional_approver (
    id                              uuid primary key,
    participant_id                  uuid not null references participant(id),
    parent_additional_approver_id   uuid null references additional_approver(id),
    level                           integer not null default 1,
    user_id                         uuid not null,
    organization_id                 uuid null,
    assigned_by                     varchar(50) not null 
                                    check (assigned_by in ('TEMPLATE', 'INITIATOR', 'PARTICIPANT', 'ADDITIONAL_APPROVER')),
    assigned_by_user_id             uuid null,
    due_at                          timestamptz null,
    due_offset                      varchar(10) not null default 'H0' 
                                    check (due_offset in ('H0', 'H3', 'H8')),
    status                          varchar(100) not null,
    recommendation                  varchar(100) null,
    is_user_editable                boolean not null default true,
    is_organization_editable        boolean not null default false,
    is_deletable                    boolean not null default true,
    assigned_at                     timestamptz not null default now(),
    recommended_at                  timestamptz null,
    created_at                      timestamptz not null default now(),
    updated_at                      timestamptz not null default now(),
    
    check (
        (assigned_by in ('TEMPLATE', 'INITIATOR', 'PARTICIPANT') 
         and parent_additional_approver_id is null)
        or
        (assigned_by = 'ADDITIONAL_APPROVER' 
         and parent_additional_approver_id is not null)
    )
);

create index ix_additional_approver_participant on additional_approver(participant_id);
create index ix_additional_approver_parent on additional_approver(parent_additional_approver_id);
create index ix_additional_approver_user on additional_approver(user_id);
create index ix_additional_approver_status on additional_approver(status);
```

**Изменения:**
- **Добавлено:** `parent_additional_approver_id`, `level`, значение `ADDITIONAL_APPROVER` в `assignedBy`.
- **Убрано:** `substitution_ref`, лимит 10.
- **Убрано:** ограничение глубины иерархии.

**Важно (ADR-016, Fix 3).** `additional_approver` **не имеет** внешнего ключа на `decision` — рекомендация хранится в собственном столбце `recommendation` этой же таблицы, а не как строка в `decision`. `additional_approver.recommendation` не участвует в вычислении агрегации (`evaluateAggregation`, см. `05_guards_actions_registry.md` §4.2) и не управляет переходами `StageStateMachine` — это чисто информационная рекомендация для основного согласующего (`participant`). Подробнее — см. §11.1 и `03_domain_model.md` §7.8.

**Правила:**
- Глубина иерархии не ограничена.
- Количество не ограничено.
- `level` вычисляется: `parent.level + 1` или 1 если нет родителя.

---

## 11. Группа 7: Решения

### 11.1 `decision` — Решение

```sql
create table decision (
    id                  uuid primary key,
    participant_id      uuid not null references participant(id),
    result               varchar(100) not null,
    comment             text null,
    auto                boolean not null default false,
    recorded_at         timestamptz not null default now(),
    created_at          timestamptz not null default now()
);

create index ix_decision_participant on decision(participant_id);
create index ix_decision_result on decision(result);
create index ix_decision_recorded on decision(recorded_at);
```

**Изменения:**
- `result` — без check (через `decision_result_registry`).
- **Не унифицировано с рекомендацией доп. согласующего (ADR-016, Fix 3).** `decision.participant_id` ссылается только на `participant` (основных согласующих/подписантов, `role IN [APPROVER, SIGNER]`). Рекомендация доп. согласующего хранится в `additional_approver.recommendation` (см. §10.1) — своей строки в `decision` она не создаёт и FK на неё не имеет. `decision` — единственный источник данных для агрегации решения этапа; `additional_approver.recommendation` в агрегации не участвует.

---

## 12. Группа 8: Замечания и комментарии

### 12.1 `remark` — Замечание

```sql
create table remark (
    id                  uuid primary key,
    process_id          uuid not null references process_instance(id),
    stage_id            uuid null references stage_instance(id),
    stage_iteration_id  uuid null references stage_iteration(id),
    participant_id      uuid null references participant(id),
    author_id           uuid not null,
    author_role         varchar(50) not null,
    text                text not null,
    status              varchar(100) not null,
    activated_by        uuid null,
    activated_at        timestamptz null,
    resolved_by         uuid null,
    resolved_at         timestamptz null,
    rejected_by         uuid null,
    rejected_at         timestamptz null,
    rejection_reason    text null,
    attachments         uuid[] not null default '{}',
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

create index ix_remark_process on remark(process_id);
create index ix_remark_stage on remark(stage_id);
create index ix_remark_stage_iteration on remark(stage_iteration_id);
create index ix_remark_status on remark(status);
create index ix_remark_author on remark(author_id);
create index ix_remark_active on remark(process_id) 
    where status in ('ACTIVE', 'IN_PROGRESS');
```

**Изменено (Fix 7.2):** `stage_iteration_idx integer null` → `stage_iteration_id uuid null references stage_iteration(id)` — было целое число без внешнего ключа, стало корректной ссылкой.

**Добавлено:** `attachments uuid[]` — вложения хранятся как массив идентификаторов файлов непосредственно в строке `remark`. Отдельных таблиц `remark_attachment` не существует (см. §4 и ADR-026): такая таблица упоминалась в сводной таблице версии 1.1, но не имела DDL и была удалена как нереализованный/расходящийся с фактической моделью вариант.

### 12.3 `comment`

```sql
create table comment (
    id                  uuid primary key,
    process_id          uuid not null references process_instance(id),
    stage_id            uuid null references stage_instance(id),
    stage_iteration_id  uuid null references stage_iteration(id),
    participant_id      uuid null references participant(id),
    author_id           uuid not null,
    author_role         varchar(50) not null,
    text                text not null,
    parent_id           uuid null references comment(id),
    attachments         uuid[] not null default '{}',
    created_at          timestamptz not null default now()
);

create index ix_comment_process on comment(process_id);
create index ix_comment_stage on comment(stage_id);
create index ix_comment_stage_iteration on comment(stage_iteration_id);
create index ix_comment_author on comment(author_id);
create index ix_comment_parent on comment(parent_id);
```

**Изменено (Fix 7.2):** `stage_iteration_idx integer null` → `stage_iteration_id uuid null references stage_iteration(id)`.

**Добавлено:** `attachments uuid[]` — как в `remark`, без отдельной таблицы `comment_attachment` (убрана, см. §4 и ADR-026).

---

## 13. Группа 9: Финальное решение

### 13.1 `final_decision`

```sql
create table final_decision (
    id                              uuid primary key,
    process_id                      uuid not null unique references process_instance(id),
    responsible_user_id             uuid not null,
    responsible_resolved_role_ref   uuid null,
    result                          varchar(100) not null,
    comment                         text null,
    recorded_at                     timestamptz not null default now(),
    created_at                      timestamptz not null default now()
);

create index ix_final_decision_process on final_decision(process_id);
create index ix_final_decision_result on final_decision(result);
```

**Убрано:** `substitution_ref`.

---

## 14. Группа 10: Архивация

### 14.1 `archive_metadata`

```sql
create table archive_metadata (
    process_id          uuid primary key references process_instance(id),
    archived_at         timestamptz not null default now(),
    previous_status     varchar(100) not null,
    restored_at         timestamptz null,
    restored_by         uuid null,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

create index ix_archive_metadata_archived on archive_metadata(archived_at);
```

---

## 15. Группа 11: Конфигурация state machine

### 15.1 `state_machine_config`

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
```

### 15.2 `state_config`

```sql
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
create index ix_state_config_config on state_config(config_id);
```

### 15.3 `transition_config`

```sql
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

---

## 16. Группа 12: Реестр guards и actions

### 16.1 `guard_registry`

```sql
create table guard_registry (
    code                    varchar(100) primary key,
    display_name            varchar(255) not null,
    description             text not null,
    handler                 varchar(255) not null,
    params_schema           jsonb null,
    scope                   varchar(50) not null 
                            check (scope in ('GLOBAL', 'CUSTOM')),
    categories              text[] not null default '{}',
    applicable_entities     text[] not null default '{}',
    is_active               boolean not null default true,
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now()
);

create index ix_guard_registry_scope on guard_registry(scope);
```

### 16.2 `action_registry`

```sql
create table action_registry (
    code                    varchar(100) primary key,
    display_name            varchar(255) not null,
    description             text not null,
    handler                 varchar(255) not null,
    params_schema           jsonb null,
    scope                   varchar(50) not null 
                            check (scope in ('GLOBAL', 'CUSTOM')),
    categories              text[] not null default '{}',
    applicable_entities     text[] not null default '{}',
    is_active               boolean not null default true,
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now()
);

create index ix_action_registry_scope on action_registry(scope);
```

---

## 17. Группа 13: (упразднена, ADR-028) — ранее Spring State Machine, персистентность

### 17.1 Историческая справка

До 2026-09-18 в этой секции описывалась таблица `ssm_state_machine_context` — персистентность Spring State Machine (`JpaRepositoryStateMachinePersist`, `machine_id = process_instance.id`, поля `state`, `event`, `extended_state` bytea, `state_machine_context` bytea).

**Таблица удалена (ADR-028).** Spring State Machine заменён собственным движком (см. `11_adr.md` ADR-028, `10_architecture.md` §7). Текущее состояние каждой сущности хранится непосредственно в её собственном поле `status` (`process_instance.status`, `stage_instance.status` и т.д., см. §7.1, §8.1, §9.1 и т.д.), уже валидируемом через `status_registry` (§5.1, ADR-015) — отдельная таблица для сериализованного контекста движка (bytea-поля `extended_state`/`state_machine_context`) больше не нужна: движок не хранит непрозрачное бинарное состояние, guards/actions работают напрямую с полями сущности через `EntityRef`/JPA.

Номер группы (13) и номер секции (§17) сохранены как исторические заглушки, а не переиспользованы для новой сущности — это сделано умышленно, чтобы не сдвигать нумерацию всех последующих секций документа (§18 Shedlock, §19 Outbox, §20 Идемпотентность, §21 Аудит, §22 Настройки уведомлений) и не ломать перекрёстные ссылки на них из других документов (`10_architecture.md`, `11_adr.md`).

**Связь с `state_machine_config`:** `state_machine_config` (§15) по-прежнему описывает карту переходов и не изменилась при замене движка — заменён только компонент, который эту карту исполнял и хранил текущее состояние.

---

## 18. Группа 14: Shedlock

### 18.1 `shedlock`

```sql
create table shedlock (
    name        varchar(64) primary key,
    lock_until  timestamptz not null,
    locked_at   timestamptz not null,
    locked_by   varchar(255) not null
);
```

**Обоснование (Fix 5, ADR-026).** Таблица упоминалась в общей схеме по группам (группа 14) начиная с версии 1.1, но не имела DDL — один из четырёх «заявленных, но не описанных» фрагментов схемы. Восстановлена со стандартной структурой, требуемой библиотекой **Shedlock 7.7.0** (см. ADR-013): `net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider` ожидает ровно эти четыре столбца.

**Использование.** Каждый `@SchedulerLock`-метод (публикация Outbox, автоархивация, автосогласование по срокам, напоминания — см. `10_architecture.md` §11) блокирует свою строку по `name` на время выполнения (`lockAtMostFor`/`lockAtLeastFor`), гарантируя, что в multi-instance развёртывании задача не выполнится дважды одновременно.

**Партиционирование:** не требуется — таблица служебная, число строк равно числу планировщик-задач (единицы).

**Хранение:** без TTL на уровне БД; `lock_until` в прошлом означает свободную блокировку.

---

## 19. Группа 15: Outbox

### 19.1 `outbox_event`

```sql
create table outbox_event (
    id                      uuid primary key,
    aggregate_type          varchar(100) not null,
    aggregate_id            uuid not null,
    event_type              varchar(255) not null,
    event_version           varchar(20) not null,
    exchange                varchar(100) not null,
    routing_key             varchar(255) not null,
    payload                 jsonb not null,
    correlation_id          uuid null,
    causation_id            uuid null,
    actor_id                uuid null,
    actor_type              varchar(50) null,
    config_version          integer null,
    occurred_at             timestamptz not null default now(),
    published_at            timestamptz null,
    publish_attempts        integer not null default 0,
    last_error              text null,
    created_at              timestamptz not null default now()
);

create index ix_outbox_unpublished on outbox_event(occurred_at) 
    where published_at is null;
create index ix_outbox_aggregate on outbox_event(aggregate_type, aggregate_id);
create index ix_outbox_event_type on outbox_event(event_type);
```

**Изменения:**
- Транспорт — RabbitMQ.

**Партиционирование:** по `occurred_at` (месяц).

---

## 20. Группа 16: Идемпотентность

### 20.1 `idempotency_key`

```sql
create table idempotency_key (
    key                 varchar(255) primary key,
    request_hash        varchar(64) not null,
    response_status     integer null,
    response_body       jsonb null,
    created_at          timestamptz not null default now(),
    expires_at          timestamptz not null
);

create index ix_idempotency_key_expires on idempotency_key(expires_at);
```

**Обоснование (Fix 5, ADR-026).** Таблица упоминалась в общей схеме по группам (группа 16) начиная с версии 1.1, но не имела DDL — второй из четырёх «заявленных, но не описанных» фрагментов схемы. Восстановлена в соответствии с механизмом `Idempotency-Key`, описанным в `07_api_contract.md` §7 (заголовок `Idempotency-Key`, срок хранения ключа — 24 часа).

**Поля:**
- `key` — значение заголовка `Idempotency-Key` (UUID), PK.
- `request_hash` — хеш тела запроса (SHA-256), чтобы отличить повторный вызов с тем же ключом, но другим телом (см. `07_api_contract.md` §7.4, ошибка `IDEMPOTENCY_CONFLICT` / `409`).
- `response_status`, `response_body` — сохранённый ответ, возвращаемый при повторном запросе с тем же ключом и тем же `request_hash`.
- `expires_at` — момент истечения (`created_at + 24 часа`).

**Очистка.** Просроченные записи (`expires_at < now()`) удаляются периодической задачей планировщика (Shedlock, см. §18) — не входит в состав API-транзакции.

---

## 21. Группа 17: Аудит

### 21.1 `audit_event`

```sql
create table audit_event (
    id                      uuid primary key,
    entity_type             varchar(100) not null,
    entity_id               uuid not null,
    action                  varchar(100) not null,
    actor_id                uuid null,
    actor_type              varchar(50) null,
    payload                 jsonb not null default '{}',
    occurred_at             timestamptz not null default now()
);

create index ix_audit_entity on audit_event(entity_type, entity_id);
create index ix_audit_actor on audit_event(actor_id);
create index ix_audit_occurred on audit_event(occurred_at);
create index ix_audit_action on audit_event(action);
```

**Партиционирование:** по `occurred_at` (месяц).

### 21.2 `config_audit_event`

```sql
create table config_audit_event (
    id                      uuid primary key,
    config_id               uuid not null references state_machine_config(id),
    config_version          integer not null,
    action                  varchar(50) not null 
                            check (action in ('CREATED', 'UPDATED', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED')),
    actor_id                uuid not null,
    diff                    jsonb not null default '{}',
    comment                 text null,
    occurred_at             timestamptz not null default now()
);

create index ix_config_audit_config on config_audit_event(config_id);
create index ix_config_audit_action on config_audit_event(action);
```

**Партиционирование:** по `occurred_at` (месяц).

---

## 22. Группа 18: Настройки уведомлений

### 22.1 `notification_settings`

```sql
create table notification_settings (
    id                          smallint primary key default 1,
    reminder_enabled            boolean not null default true,
    reminder_interval_hours     integer not null default 24 
                                check (reminder_interval_hours > 0),
    updated_at                  timestamptz not null default now(),
    updated_by                  uuid null,

    check (id = 1)
);
```

**Обоснование (ADR-027).** business_context (§17.2) требует, чтобы периодичность напоминаний инициатору о процессах в статусе «На доработке» настраивалась администратором. До этой версии `10_architecture.md` §11 описывал `ReminderJob` с фиксированной периодичностью «раз в день», а заявленная настраиваемость нигде не была реализована технически (открытый пробел, см. `claude/gap-analysis-business-context-vs-architecture.md`). `notification_settings` устраняет этот пробел минимальным способом: **одна глобальная настройка**, без гранулярности per-шаблон или per-тип процесса — по аналогии с `ArchiveSettings` (`04_state_machines.md` §13.3), которые тоже только глобальные.

**Singleton.** Таблица всегда содержит ровно одну строку с `id = 1` (обеспечено через `CHECK (id = 1)` и предзаполненный `INSERT` в миграции). `UPDATE` — единственная операция изменения; `INSERT`/`DELETE` не используются приложением после первичной миграции.

**Поля:**
- `reminder_enabled` — включены ли напоминания (`false` = `ReminderJob` не выполняет рассылку, но продолжает быть запланированным планировщиком).
- `reminder_interval_hours` — периодичность в часах, по умолчанию 24 (соответствует прежнему хардкоду «раз в день»); настраивается администратором через `PUT /admin/notification-settings` (`07_api_contract.md` §26).
- `updated_at`, `updated_by` — аудит последнего изменения (полный аудит изменений — при необходимости через `audit_event`, §21, по общему механизму; отдельной append-only истории для этой настройки не вводится, так как настройка одна и её текущее значение важнее истории).

**Использование.** `ReminderJob` (`10_architecture.md` §11.1, §11.3) читает `reminder_interval_hours`/`reminder_enabled` при каждом запуске (либо кэширует с коротким TTL) вместо хардкода периода — см. ADR-027.

---

## 23. Индексы

### 23.1 Сводка

| Таблица | Индекс | Тип | Назначение |
|---|---|---|---|
| `template` | `ux_template_name_version` | Unique | Уникальность |
| `process_instance` | `ix_process_entity` | B-tree | Поиск по сущности |
| `process_instance` | `ix_process_status` | B-tree | Фильтр по статусу |
| `process_instance` | `ix_process_aggregate` | B-tree | Поиск по агрегату |
| `process_instance` | `ux_process_revision` | Unique | Уникальность ревизии |
| `process_instance` | `ix_process_auto_archive` | Partial | Планировщик |
| `stage_instance` | `ix_stage_instance_due` | Partial | Автосогласование |
| `stage_iteration` | `ux_stage_iteration` | Unique | Уникальность |
| `participant` | `ux_participant_iteration_user` | Unique | Уникальность |
| `participant` | `ix_participant_due` | Partial | Сроки задач |
| `participant` | `ix_participant_order` | B-tree | Очередь Sequential (ADR-022) |
| `additional_approver` | `ix_additional_approver_parent` | B-tree | Иерархия |
| `remark` | `ix_remark_active` | Partial | Активные замечания |
| `outbox_event` | `ix_outbox_unpublished` | Partial | Неопубликованные |
| `idempotency_key` | `ix_idempotency_key_expires` | B-tree | Очистка просроченных |
| `audit_event` | `ix_audit_occurred` | B-tree | Поиск по дате |

---

## 24. Ограничения

### 24.1 Внешние ключи

Все связи защищены через `REFERENCES` с `ON DELETE CASCADE`.

### 24.2 Check-констрейнты

- `template.status`, `template.process_type`.
- `stage_template.duration > 0`, `stage_template.stage_type`, `decision_mode`, `execution_order`.
- `slot_template.slot_type`, `due_offset`, `is_organization_editable → is_user_editable`.
- `participant.role`.
- `additional_approver.assigned_by`, `due_offset`, check иерархии.
- `state_machine_config.entity_type`, `status`.
- `transition_config.trigger`.
- `guard_registry.scope`, `action_registry.scope`.
- `config_audit_event.action`.
- `notification_settings.id = 1` (singleton), `reminder_interval_hours > 0` (ADR-027).

### 24.3 Уникальные ограничения

| Таблица | Уникальность |
|---|---|
| `template` | `(name, version) where status in (DRAFT, PUBLISHED)` |
| `stage_template` | `(template_id, order_idx)` |
| `process_instance` | `(document_aggregate_id, revision_label) where document_aggregate_id is not null` |
| `stage_instance` | `(process_id, order_idx)` |
| `stage_iteration` | `(stage_id, iteration_idx)` |
| `participant` | `(stage_iteration_id, user_id, role)` |
| `final_decision` | `(process_id)` |
| `state_machine_config` | `(entity_type, process_type, version)` |
| `state_config` | `(config_id, code)` |
| `transition_config` | `(config_id, code)` |

---

## 25. Партиционирование

| Таблица | Стратегия | Ключ |
|---|---|---|
| `outbox_event` | По времени | `occurred_at` (месяц) |
| `audit_event` | По времени | `occurred_at` (месяц) |
| `config_audit_event` | По времени | `occurred_at` (месяц) |

**Политика хранения:**

| Таблица | Срок |
|---|---|
| `outbox_event` | 30 дней |
| `audit_event` | 5 лет |
| `config_audit_event` | Бессрочно |
| `idempotency_key` | 24 часа (см. §20) |

---

## 26. Read-модели для агрегированных эндпоинтов

### 26.1 JOIN-запросы (базовый)

Для `/entities/approval-view` — JOIN-запросы с `@EntityGraph` и batch-загрузкой.

### 26.3 Кэширование

- Redis для часто запрашиваемых процессов.
- Invalidation по событиям.

---

## 27. Миграции

- **Инструмент:** Flyway (или Liquibase).
- **Формат:** `V<version>__<description>.sql`.
- **Правила:** атомарность, только forward, тестировать на копии.

---

## 28. Сводная таблица

| № | Таблица | Группа | Партиционирование |
|---|---|---|---|
| 1 | `status_registry` | Справочники | — |
| 2 | `decision_result_registry` | Справочники | — |
| 3 | `template` | Шаблоны | — |
| 4 | `stage_template` | Шаблоны | — |
| 5 | `slot_template` | Шаблоны | — |
| 6 | `applicability_rule` | Шаблоны | — |
| 7 | `process_instance` | Процессы | — |
| 8 | `stage_instance` | Этапы | — |
| 9 | `stage_iteration` | Этапы | — |
| 10 | `participant` | Участники | — |
| 11 | `additional_approver` | Доп. | — |
| 12 | `decision` | Решения | — |
| 13 | `remark` | Замечания | — |
| 14 | `comment` | Комментарии | — |
| 15 | `final_decision` | Финал | — |
| 16 | `archive_metadata` | Архив | — |
| 17 | `state_machine_config` | SSM | — |
| 18 | `state_config` | SSM | — |
| 19 | `transition_config` | SSM | — |
| 20 | `guard_registry` | Реестр | — |
| 21 | `action_registry` | Реестр | — |
| 22 | `shedlock` | Shedlock | — |
| 23 | `outbox_event` | Outbox | По месяцу |
| 24 | `idempotency_key` | Идемпотентность | — |
| 25 | `audit_event` | Аудит | По месяцу |
| 26 | `config_audit_event` | Аудит | По месяцу |
| 27 | `notification_settings` | Настройки уведомлений | — |

**Итого: 27 таблиц в 17 группах (группа «13. SSM persistence» упразднена без переиспользования номера — ADR-028).**

**Изменения от версии 1.1 (ADR-023, ADR-026):** таблица `template_version` (была №4) удалена — версионирование шаблонов через `parent_template_id`; таблицы `remark_attachment` (была №15) и `comment_attachment` (была №17) удалены полностью — вложения хранятся как `attachments uuid[]` в `remark`/`comment`; таблицы `shedlock` и `idempotency_key`, ранее заявленные без DDL, теперь описаны в §18 и §20.

**Изменения от 2026-09-18 (ADR-027):** добавлена таблица `notification_settings` (группа 18) — singleton-настройка периодичности напоминаний, настраиваемая администратором через `07_api_contract.md` §26. Закрывает открытый пробел «Напоминания» из `claude/gap-analysis-business-context-vs-architecture.md`.

**Изменения от 2026-09-18 (ADR-028):** таблица `ssm_state_machine_context` (ранее №22, группа «13. SSM persistence») **удалена** — движок машины состояний заменён собственной реализацией; текущее состояние сущности хранится в её собственном поле `status` (уже валидируемом через `status_registry`, ADR-015), отдельная таблица персистентности контекста движка не требуется. Итоговое число таблиц уменьшилось с 28 до 27; строки таблицы после удалённой перенумерованы без пропусков (были №23–28, стали №22–27). Подробности замены — `11_adr.md` ADR-028, `10_architecture.md` §7.

---
