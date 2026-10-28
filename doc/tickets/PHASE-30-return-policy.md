# PHASE-30 — Политика возврата на уровне шаблона

> Новый тикет вне первоначального плана фаз. Реализует **ADR-029** (`doc/arch/11_adr.md` §31).
> Закрывает DEFECT-12 (проверка цели возврата, несколько целей, явный отказ вместо «тихого»
> успеха) и даёт универсальный механизм для возврата после доработки и после отзыва.
> Ветка: `feature/phase-30-return-policy`.

## Статус

`Не начато`

## Контекст

Сейчас «куда возвращаться» задаётся полем `stage_template.allowed_return_stages` — плоским
списком `orderIdx`, который копируется в `stage_instance.allowed_return_stages`. Механизм не
позволяет выразить «после доработки **обязательно** вернуть на входной контроль»: он умеет
только «можно вернуть на один из перечисленных». Из-за этого бизнес-требование подталкивает к
костылю «если в маршруте есть этап с типом `INPUT_CONTROL`, возвращать всегда на него» — скрытой
логике в коде, которая ломается на втором этапе того же типа, на шаблоне без такого этапа и на
шаблоне, где возврат нужен только с части этапов.

Дополнительно:

- `allowedReturnStages` адресует этапы по `orderIdx`: вставка этапа в середину маршрута сдвигает
  номера и молча меняет смысл правила;
- у отзыва нет политики вообще: после `Recalled` маршрут либо перезапускается с первого этапа,
  либо процесс остаётся отозванным;
- guard `IsTargetStageAllowed` список не читает (`target <= onRework`), поэтому запрещённый
  шаблоном возврат проходит;
- клиент не получает признак «есть несколько целей», а запрещённая цель даёт `200` с неизменным
  статусом вместо бизнес-отказа.

Целевая модель — **политика возврата** в шаблоне: правила «причина + откуда → режим + куда»,
где цель — конкретный этап по стабильному ключу, а не по типу и не по `orderIdx`.

## Источники (для истории, не для перехода)

- `doc/arch/11_adr.md` §31 — ADR-029, принятое решение.
- `doc/tickets/DEFECT-12-allowed-return-stages.md` — дефекты, которые закрываются.
- `doc/arch/03_domain_model.md` §6.4, §7.3 — `StageTemplate`, `StageInstance`.
- `doc/arch/04_state_machines.md` §11, §14 — выбор целевого этапа, отзыв.
- `doc/arch/07_api_contract.md` §12.8–12.9 — recall/resume.
- `doc/AUDIT-2026-10-27-universality.md` §4.4.3 — почему логика на тип этапа — антипаттерн.

## Объём фазы (Scope)

1. Стабильный ключ этапа `stage_template.key` (уникальный в шаблоне, неизменяемый в
   опубликованной версии).
2. Таблицы `return_reason_registry`, `template_return_rule`, `template_return_rule_target`;
   удаление `stage_template.allowed_return_stages` и `stage_instance.allowed_return_stages`.
3. Компиляция правил в эффективную политику «этап × причина» при публикации шаблона.
4. Снимок политики в процессе (`process_instance.return_policy`) при генерации маршрута.
5. Единый резолвер `ReturnPolicyService` для guard, API и UI.
6. Интеграция с доработкой: `ResumeProcess`, guard, авто-подстановка `FORCED`, `422` на
   запрещённую цель.
7. Интеграция с отзывом: сохранение маршрута, отмена активной работы, `ResumeAfterRecall`,
   сброс этапов после цели, новая итерация при повторной активации.
8. API шаблона (`stages[].key`, `returnRules`) и API процесса (`return-options`, `resume`).
9. Валидация при публикации (включая запрет возврата «вперёд» и полноту покрытия).
10. Flyway-миграция существующих шаблонов и процессов.
11. Тесты (unit + интеграционные на Testcontainers).

## Явно не входит (Out of scope)

- Условия `when` в правилах — зарезервированы в модели, включаются отдельным этапом (см. §12).
- Политика для ревизий документа (PHASE-26) — отдельная причина возврата, если понадобится.
- Редактирование политики на уровне процесса (override) — политика принадлежит только шаблону.
- UI-редактор политики (в этом тикете — API и серверная логика; визуализация матрицы — отдельно).
- Изменение правил финального решения UNIFIED, кроме последствий отзыва.
- Пересмотр `allowedReturnStages` в старых документах — миграция должна сохранить поведение.

## Что уже есть в репозитории

- `src/main/java/ru/coordination/approval/domain/template/StageTemplate.java` — этап шаблона,
  поле `allowedReturnStages` (удаляется).
- `src/main/java/ru/coordination/approval/domain/process/StageInstance.java` — этап процесса,
  поле `allowedReturnStages` (удаляется).
- `src/main/java/ru/coordination/approval/service/RouteGenerationService.java` — генерация
  маршрута, здесь же нужно строить снимок политики.
- `src/main/java/ru/coordination/approval/service/TemplateService.java` — создание/обновление
  шаблона, здесь же валидация ключей и правил.
- `src/main/java/ru/coordination/approval/service/action/ValidateTemplateStructureAction.java` —
  валидация при публикации.
- `src/main/java/ru/coordination/approval/service/guard/IsTargetStageAllowedGuard.java` —
  заменяется на guard, читающий политику.
- `src/main/java/ru/coordination/approval/service/ProcessService.java` — `resume`, `recall`.
- `src/main/java/ru/coordination/approval/api/controller/ProcessController.java` — `resume`,
  `recall`; `targetStageId` → `orderIdx`.
- `src/main/java/ru/coordination/approval/service/action/ActivateTargetStageAction.java`,
  `RejectStagesFromAction.java` — механика возврата.
- `src/main/resources/db/migration/V23__seed_process_rework_resume_transitions.sql` — текущие
  переходы `ResumeProcess`/`RecallProcess`/`ReactivateStage`.
- Flyway-миграции до `V25`; новые — `V26`, `V27`.

## Технические требования

### 1. Ключ этапа

1.1. Добавить `stage_template.key varchar(100) not null`; уникальность `(template_id, key)`;
формат `^[a-z][a-z0-9_]{1,99}$`.

1.2. Ключ назначается при создании этапа. В опубликованной версии шаблона ключ неизменяем; в
`Draft` допускается переименование (процессов на Draft нет). Смена ключа в опубликованной версии
— это новая версия шаблона. При создании новой версии/форка правила копируются вместе с
этапами, ключи сохраняются, ссылки в правилах перепривязываются по ключам (не по `orderIdx`).

1.3. DTO: `api/dto/template/StageTemplateDto` и `service/dto/StageTemplateDto` получают поле
`key`; `TemplateRequestMapper`/`TemplateMapper` пробрасывают его; `TemplateDto` отдаёт ключ в
ответе. Если старый клиент не передал `key`, сервис генерирует `stage_<orderIdx>` (как в
миграции) и возвращает его в ответе — запрос без ключей остаётся валидным.

1.4. Для существующих шаблонов миграция заполняет `key = 'stage_' || order_idx`; коллизий нет,
так как `orderIdx` уникален в шаблоне.

### 2. Схема БД (V26)

Новые объекты и их роль:

| Объект | Роль |
|---|---|
| `stage_template.key` | стабильный адрес этапа внутри версии шаблона; на него ссылаются правила |
| `return_reason_registry` | справочник причин возврата (`REWORK`, `RECALL`, …) |
| `template_return_rule` | правило: в шаблоне `T`, для причины `R`, откуда `K` (или все), режим `M` |
| `template_return_rule_target` | цели правила: 1..N конкретных этапов |
| `process_instance.return_policy` | неизменяемый снимок скомпилированной политики для процесса |

Остальные атрибуты существующих таблиц `stage_template` и `process_instance` не меняются; ниже
описаны только новые объекты и изменяемые колонки.

#### 2.1 `stage_template.key` — ключ этапа

**Зачем.** Правилам нужен адрес этапа, который не меняется при переименовании, перестановке и
создании новой версии шаблона. `orderIdx` для этого не годится (сдвигается при вставке этапа),
`stage_template.id` — тоже (новый UUID в каждой версии). Ключ — это имя этапа внутри шаблона,
например `input_control`, `legal_review`, `signing`.

```sql
alter table stage_template add column key varchar(100);
update stage_template set key = 'stage_' || order_idx where key is null;
alter table stage_template alter column key set not null;
create unique index ux_stage_template_key on stage_template(template_id, key);
```

| Атрибут | Тип | Обяз. | Зачем / что означает |
|---|---|---|---|
| `key` | `varchar(100)` | да | Уникальное имя этапа в пределах шаблона. Используется в правилах (`from_stage_key`, `target_stage_key`) и в API при создании/обновлении шаблона, когда у этапов ещё нет `id`. Формат `^[a-z][a-z0-9_]{1,99}$`. |

**Ограничения.**

- `unique (template_id, key)` — ключ уникален в шаблоне, но не глобально; поэтому в правилах он
  всегда рассматривается вместе с `template_id`.
- В опубликованной версии ключ неизменяем; переименование — только в `Draft` (процессов на нём
  нет). Смена ключа в опубликованной версии — это новая версия шаблона.
- Для существующих шаблонов миграция заполняет `key = 'stage_' || order_idx`; если старый клиент
  не передал ключ, сервис генерирует его так же.

#### 2.2 `return_reason_registry` — справочник причин возврата

**Зачем.** Отделить причину возврата от кода и от конкретной карты переходов: правило ссылается
на код причины, UI берёт подпись, валидатор — список допустимых типов процесса. Добавление новой
причины (например, `REVISION`) не требует менять схему таблиц правил, хотя и потребует проводки
нового перехода в карте.

```sql
create table return_reason_registry (
    code                     varchar(50) primary key,
    display_name             varchar(255) not null,
    applicable_process_types varchar(20)[] not null,
    is_active                boolean not null default true,
    created_at               timestamp not null,
    updated_at               timestamp not null,
    constraint ck_return_reason_types check (cardinality(applicable_process_types) > 0)
);

insert into return_reason_registry(code, display_name, applicable_process_types, created_at, updated_at)
values
    ('REWORK', 'Доработка', '{STANDARD}', now(), now()),
    ('RECALL', 'Отзыв',     '{STANDARD,UNIFIED}', now(), now());
```

| Атрибут | Тип | Обяз. | Зачем / что означает |
|---|---|---|---|
| `code` | `varchar(50)` | да, PK | Стабильный код причины (`REWORK`, `RECALL`). На него ссылаются `template_return_rule.reason_code` и снимок процесса. Не enum, чтобы подпись и применимость были данными, а не релизом. |
| `display_name` | `varchar(255)` | да | Подпись для UI; меняется без изменения кода и правил. |
| `applicable_process_types` | `varchar(20)[]` | да | К каким типам процесса причина применима: `REWORK` — только `STANDARD`, `RECALL` — оба. Компилятор и валидатор не дадут использовать `REWORK` в `UNIFIED`. |
| `is_active` | `boolean` | да, по умолчанию `true` | Мягкое отключение причины: старые правила и снимки остаются валидными, новые правила с неактивной причиной не создаются. |
| `created_at` | `timestamp` | да | Когда причина заведена; аудит и эксплуатация. |
| `updated_at` | `timestamp` | да | Когда менялась подпись/применимость; аудит. |

**Ограничения.** PK по `code`; отдельного `id` нет — код и есть естественный ключ. Проверка
`cardinality(applicable_process_types) > 0` (причины без типа процесса бессмысленны).

#### 2.3 `template_return_rule` — правило возврата

**Зачем.** Одна строка — одно правило: «в шаблоне `T` для причины `R`, начиная с этапа `K` (или
со всех этапов), вернуть по режиму `M` на цели из дочерней таблицы». Это единица авторства,
валидации и копирования в новую версию шаблона.

```sql
create table template_return_rule (
    id              uuid primary key,
    template_id     uuid not null references template(id) on delete cascade,
    reason_code     varchar(50) not null references return_reason_registry(code),
    from_stage_key  varchar(100) null,
    mode            varchar(20) not null,
    when_condition  jsonb null,
    priority        integer not null default 0,
    created_at      timestamp not null,
    constraint ck_return_rule_mode check (mode in ('FORCED','CHOICE','DENY')),
    constraint ck_return_rule_deny_reason check (mode <> 'DENY' or reason_code = 'RECALL')
);

create unique index ux_return_rule_unconditional
    on template_return_rule(template_id, reason_code, coalesce(from_stage_key, ''))
    where when_condition is null;

create index ix_return_rule_template on template_return_rule(template_id);
```

| Атрибут | Тип | Обяз. | Зачем / что означает |
|---|---|---|---|
| `id` | `uuid` | да, PK | Технический идентификатор правила. Нужен для FK целей и для `ruleId` в снимке (аудит: какое правило сработало). |
| `template_id` | `uuid` | да, FK → `template(id)` | Владелец политики — конкретная версия шаблона. Нужен и для правил «для всех этапов» (`from_stage_key = null`), и потому, что ключ этапа уникален только внутри шаблона. При удалении черновика шаблона правила удаляются каскадно. |
| `reason_code` | `varchar(50)` | да, FK → `return_reason_registry.code` | Какая причина: доработка или отзыв. Определяет, в каком переходе применяется правило. |
| `from_stage_key` | `varchar(100)` | нет | Область действия правила: `null` — все этапы шаблона (правило по умолчанию), значение — только этап с этим ключом. Так «любая доработка → входной контроль» остаётся одной строкой, а не N строками. |
| `mode` | `varchar(20)` | да | Что разрешено: `FORCED` — ровно одна цель, сервер подставляет её сам; `CHOICE` — 1..N целей, клиент выбирает; `DENY` — возврата нет (только для `RECALL`). |
| `when_condition` | `jsonb` | нет | Зарезервировано под условия (`when`) следующего этапа. В v1 обязано быть `null`; колонка существует, чтобы включение условий не требовало миграции таблицы. |
| `priority` | `integer` | да, по умолчанию `0` | Порядок применения условных правил (меньше — раньше). В v1 не используется, так как на пару «шаблон × причина × откуда» допускается одно безусловное правило; нужен для v1.1. |
| `created_at` | `timestamp` | да | Когда правило создано; аудит. |

**Ограничения и почему они такие.**

- `ck_return_rule_mode` — в `mode` только три известных значения; опечатка не пройдёт.
- `ck_return_rule_deny_reason` — `DENY` допустим только для `RECALL`: при доработке отклонение
  всегда ведёт в `OnRework`, и «запретить возврат» означало бы оставить процесс без выхода.
- `ux_return_rule_unconditional` — одно безусловное правило на `(шаблон, причина, откуда)`.
  `coalesce(from_stage_key, '')` нужен, потому что `null` (правило по умолчанию) тоже должен быть
  уникальным; частичный индекс `where when_condition is null` оставляет место для нескольких
  условных правил в v1.1.
- `from_stage_key` — не FK: ключ этапа уникален только в паре с `template_id`, а поле nullable.
  Принадлежность проверяет компилятор при публикации; так проще, чем составной FK с nullable-
  колонкой.

#### 2.4 `template_return_rule_target` — цели правила

**Зачем.** У правила может быть несколько целей (`CHOICE`), одна (`FORCED`) или ни одной
(`DENY`). Отдельная таблица позволяет хранить 1..N целей без массивов и дублей и проверять
уникальность цели в правиле.

```sql
create table template_return_rule_target (
    id                uuid primary key,
    rule_id           uuid not null references template_return_rule(id) on delete cascade,
    target_stage_key  varchar(100) not null
);

create unique index ux_return_target
    on template_return_rule_target(rule_id, target_stage_key);
```

| Атрибут | Тип | Обяз. | Зачем / что означает |
|---|---|---|---|
| `id` | `uuid` | да, PK | Технический идентификатор строки; удобно для JPA и точечных правок. |
| `rule_id` | `uuid` | да, FK → `template_return_rule(id)` | К какому правилу относится цель. При удалении правила цели удаляются каскадно. |
| `target_stage_key` | `varchar(100)` | да | Конкретный этап, на который возвращаемся. Всегда ключ, а не `SELF`/`FIRST`: «на себя» — ключ самого этапа, «на первый» — ключ первого этапа. Принадлежность этапа тому же шаблону проверяет компилятор. |

**Ограничения.** `ux_return_target (rule_id, target_stage_key)` — одна и та же цель не
дублируется. Количество целей (`FORCED` = 1, `CHOICE` ≥ 1, `DENY` = 0) проверяется при
публикации, а не CHECK-констрейнтом: оно зависит от `mode` родительского правила, а не от одной
строки.

#### 2.5 `process_instance.return_policy` — снимок политики

**Зачем.** В рантайме процесс не должен читать шаблон: правила уже скомпилированы в конкретные
`stageInstanceId` и зафиксированы на старте (ADR-003, Snapshot-on-Start). Один и тот же снимок
читают guard, API и UI — поэтому они не расходятся, как это было с `allowedReturnStages`.

```sql
alter table process_instance add column return_policy jsonb not null default '[]';
```

| Атрибут | Тип | Обяз. | Зачем / что означает |
|---|---|---|---|
| `return_policy` | `jsonb` | да, по умолчанию `[]` | Массив записей «этап × причина → режим + цели». Для старых процессов миграция заполняет его из `stage_instance.allowed_return_stages`; для новых — генератор маршрута. |

Запись снимка:

| Поле | Тип | Зачем / что означает |
|---|---|---|
| `reason` | `string` | Код причины (`REWORK`/`RECALL`); по нему резолвер выбирает запись. |
| `fromStageId` | `uuid` | Этап, к которому применяется запись (конкретный `stage_instance`). Одна запись на пару «этап × причина». |
| `mode` | `string` | Эффективный режим `FORCED`/`CHOICE`/`DENY` уже с учётом приоритета правил. |
| `targetStageIds` | `uuid[]` | Конкретные этапы-цели; для `DENY` — пустой массив. Рантайму не нужно резолвить ключи. |
| `ruleId` | `uuid` | Какое правило шаблона дало запись; для аудита и разбора инцидентов. Не используется для выбора цели. |

**Почему JSONB, а не таблица.** Снимок читается целиком по `process_id`, не обновляется и не
участвует в JOIN; его размер — число этапов × число причин, то есть десятки строк. Отдельная
таблица дала бы FK-целостность, но добавила бы JOIN на каждый возврат и вторую точку правки
снимка. Если появится запрос «покажи все процессы, которые могут вернуться на этап X», можно
сделать проекцию/индекс отдельно, не меняя источник истины.

#### 2.6 Удаляемые колонки

| Колонка | Почему удаляется |
|---|---|
| `stage_template.allowed_return_stages` | Плоский список `orderIdx` не выражает «должен/может», не знает причин и ломается при вставке этапа. Заменяется правилами `template_return_rule`. |
| `stage_instance.allowed_return_stages` | Дублировал политику в каждом этапе и расходился с guard/UI. Заменяется снимком `process_instance.return_policy`. |

Порядок миграции: создать новые таблицы → заполнить правила из старых списков → собрать снимки
для существующих процессов → удалить обе колонки. Откат — обратная миграция, но она не
восстановит снимки, созданные после включения новой версии; это допустимо, так как колонки
удаляются в том же релизе.

#### 2.7 Сводка ограничений

| Объект | Зачем |
|---|---|
| `ux_stage_template_key (template_id, key)` | Ключ уникален в шаблоне; правило не может сослаться на два разных этапа с одним ключом. |
| `ux_return_rule_unconditional` | Одно безусловное правило на пару «шаблон × причина × откуда»; не даёт двум правилам противоречить друг другу. |
| `ux_return_target (rule_id, target_stage_key)` | Одна цель не дублируется в правиле. |
| `ck_return_rule_mode` | В `mode` только `FORCED`/`CHOICE`/`DENY`. |
| `ck_return_rule_deny_reason` | `DENY` только для `RECALL`; для `REWORK` это оставило бы процесс без выхода. |
| `ix_return_rule_template` | Выборка правил шаблона при публикации/копировании версии; частичный уникальный индекс для этого не всегда подходит (условные правила в v1.1). |

Порядок цели не хранится: `target_stage_key` резолвится компилятором в конкретный этап шаблона.
Селекторов `SELF`/`FIRST` нет: «на себя» — это ключ самого этапа, «на первый» — ключ первого
этапа; для правила «каждый этап — на себя» создаётся правило на этап.

> **Почему `template_id` и `from_stage_key` — не две привязки.** `template_id` — владелец политики
> (конкретная версия шаблона); он нужен для правил «для всех этапов» (`from_stage_key = null`) и
> потому, что ключ этапа уникален только внутри шаблона. `from_stage_key` — область действия
> внутри этой политики: конкретный этап или все. Полный адрес этапа — пара `(template_id, key)`.
> Правило не вешается на строку `stage_template`: иначе «все этапы → входной контроль» пришлось
> бы дублировать N раз и добавлять строку при каждом новом этапе.

#### 2.8 Пример: правила → эффективная политика → снимок

Шаблон `T` (версия 7), этапы: `input_control` (order 1), `legal_review` (2), `signing` (3).

Правила в `template_return_rule` / `template_return_rule_target`:

| Правило | `reason_code` | `from_stage_key` | `mode` | Цели |
|---|---|---|---|---|
| R1 | `REWORK` | `null` (все этапы) | `FORCED` | `input_control` |
| R2 | `REWORK` | `signing` | `CHOICE` | `signing`, `input_control` |
| R3 | `RECALL` | `null` (все этапы) | `FORCED` | `input_control` |

Что скомпилируется:

| Этап | `REWORK` | `RECALL` |
|---|---|---|
| `input_control` | R1: только `input_control` | R3: только `input_control` |
| `legal_review` | R1: только `input_control` | R3: только `input_control` |
| `signing` | R2: `signing` или `input_control` (выбор) | R3: только `input_control` |

Для этапа `signing` в `process_instance.return_policy` попадут две записи:

```json
[
  {"reason": "REWORK", "fromStageId": "<signing>", "mode": "CHOICE",
   "targetStageIds": ["<signing>", "<input_control>"], "ruleId": "<uuid R2>"},
  {"reason": "RECALL", "fromStageId": "<signing>", "mode": "FORCED",
   "targetStageIds": ["<input_control>"], "ruleId": "<uuid R3>"}
]
```

Так видно, зачем нужны все три уровня: `template_return_rule` — авторское правило, `target` —
его цели, `return_policy` — уже разрешённый для конкретного процесса результат, с которым
работает guard/API/UI.

### 3. Доменные сущности

3.1. `ReturnReason` — не enum, а код из `return_reason_registry`; в коде — константы
`ReturnReasons.REWORK`, `ReturnReasons.RECALL` для известных сценариев.

3.2. `ReturnMode` (`FORCED`, `CHOICE`, `DENY`).

3.3. `Template` получает `@OneToMany` `returnRules` (каскадное сохранение, orphanRemoval).
`ReturnRule` и `ReturnRuleTarget` — JPA-сущности.

3.4. `ProcessInstance.returnPolicy` — JSONB-поле с типом `List<ReturnPolicyEntry>` (Hibernate
`@JdbcTypeCode(SqlTypes.JSON)`), снимок; после старта не меняется (кроме перекомпиляции при
редактировании маршрута после отзыва, если такое редактирование появится).

### 4. Компиляция политики

`ReturnPolicyCompiler.compile(Template template)` → `Map<String stageKey, Map<String reason, EffectiveRule>>`.

Алгоритм:

1. Для каждой причины из `return_reason_registry`, применимой к `template.processType`:
   - для каждого этапа `S`: если есть правило с `from_stage_key = S.key`, оно обязано быть
     применимо (все его цели `order_idx <= S.orderIdx`); иначе — ошибка публикации: правило,
     привязанное к этапу, не может указывать «вперёд»;
   - иначе искать правило по умолчанию (`from_stage_key is null`), применимое к `S` (все цели
     `order_idx <= S.orderIdx`); конкретное правило важнее правила по умолчанию;
   - если ни одно правило не применимо — ошибка компиляции с указанием `stage key` и `reason`.
2. Проверить режим и количество целей: `FORCED` — ровно 1, `CHOICE` — ≥ 1, `DENY` — 0; дубли
   целей запрещены.
3. Резолв целей: `target_stage_key` → конкретный этап шаблона; ключ обязан существовать.
4. Вернуть эффективную политику; её же использовать для предпросмотра в API шаблона.

Ошибки компиляции — `IllegalArgumentException` с человекочитаемым текстом (как в
`ValidateTemplateStructureAction`), публикация откатывается транзакцией.

### 5. Снимок в процессе

В `RouteGenerationService.buildProcess` после построения этапов:

1. вызвать компилятор;
2. заменить `stageKey` → `StageInstance.id` (карта уже есть в цикле генерации);
3. записать в `process.returnPolicy` список записей:

```json
[
  {
    "reason": "REWORK",
    "fromStageId": "11111111-...",
    "mode": "FORCED",
    "targetStageIds": ["22222222-..."],
    "ruleId": "33333333-..."
  }
]
```

Одна запись на пару «этап × причина». `ruleId` — ссылка на правило шаблона для аудита; для
самого возврата достаточно `mode` и `targetStageIds`.
Для `DENY` — `targetStageIds: []`.

Снимок неизменяем после старта. Если в будущем появится редактирование маршрута после отзыва,
снимок перекомпилируется по ключам; исчезновение ключа, на который настроен возврат, — ошибка
сохранения маршрута.

### 6. Резолвер

`ReturnPolicyService`:

```java
ReturnOptions resolve(ProcessInstance process, String reasonCode);
ReturnOptions resolve(ProcessInstance process, String reasonCode, UUID fromStageId);
```

`ReturnOptions`: `reason`, `fromStageId`, `mode`, `targets` (`StageInstance`), `defaultTargetId`,
`allowed` (false для `DENY`).

Определение источника (`fromStageId`), если не передан:

- `REWORK` — этап со статусом `OnRework` (ровно один; если нет — ошибка конфигурации);
- `RECALL`:
  1. этап со статусом `Active`;
  2. иначе этап со статусом `OnRework`;
  3. иначе последний по `orderIdx` этап со статусом `Completed`/`Approved`/`ApprovedWithComments`;
  4. иначе маршрут ещё не запускался (`PendingResponsibleApproval`) — политика не применяется,
     см. §8.7.

`defaultTargetId`: для `FORCED` — единственная цель; для `CHOICE` — цель, если она одна, иначе
`null`; для `DENY` — `null`.

Рантайм-проверка (защита в глубину): выбранная цель существует в процессе и её `orderIdx` не
больше `orderIdx` источника. Нарушение — `ReturnTargetNotAllowedException` (422).

### 7. Доработка

7.1. Заменить `IsTargetStageAllowedGuard` на `IsReturnTargetAllowedGuard` (код реестра
`IsReturnTargetAllowed`): читает снимок, причину `REWORK`, источник — `OnRework`-этап; проверяет,
что `targetStageId` входит в цели, либо при `FORCED` совпадает с единственной целью. Старый guard
и его запись в `guard_registry` деактивировать (не удалять, чтобы не ломать исторические карты).

7.2. `ProcessService.resume`:

- принимать `reasonCode` и `targetStageId` (не `orderIdx`);
- `REWORK` допустим только при статусе `OnRework`, `RECALL` — только при `Recalled`; иначе `422
  RETURN_REASON_MISMATCH`;
- резолвить политику: `FORCED` — если клиент не передал цель, подставить; если передал другую —
  `422 RETURN_TARGET_NOT_ALLOWED`; `CHOICE` — цель обязательна и должна входить в список, иначе
  `422`; `DENY` — `409 RETURN_NOT_ALLOWED`;
- передавать в переход `targetStageId` (действия `RejectStagesFrom`/`ActivateTargetStage`
  переводятся на `targetStageId`; `orderIdx` остаётся для сортировки/UI);
- `performed == false` — не «тихий успех», а `422` с причиной (см. DEFECT-12/06).

7.3. `RejectStagesFromAction` — без изменения семантики (этапы между целью и `OnRework` →
`Rejected`), но принимает `targetStageId`.

7.4. `ActivateTargetStageAction` — принимает `targetStageId`, создаёт новую итерацию и клонирует
участников (как сейчас).

### 8. Отзыв

8.1. `RecallProcess` (`InProgress`/`OnRework` → `Recalled`) получает действие
`CancelProcessWork`, которое **пишет статусы напрямую** (то же документированное исключение, что
и `RejectStagesFrom`: отдельных переходов отмены в карте нет):

- активный этап → `Cancelled`;
- активная итерация → `Cancelled`;
- участники в `Assigned`/`InProgress` → `Cancelled`;
- процесс → `Recalled` (самим переходом).

Маршрут (все `StageInstance`) сохраняется. Если позже появятся переходы `CancelStage` /
`CancelStageUnified` в картах, действие переводится на них.

8.2. Новый переход **`ResumeAfterRecall`** (`Recalled → InProgress`) в картах `PROCESS/STANDARD` и
`PROCESS/UNIFIED`:

- guards: `IsInitiator`, `IsReturnTargetAllowed` (причина `RECALL`), `AllRemarksProcessed`
  (решение: требовать, как при доработке; если бизнес скажет иначе — убрать для `RECALL`);
- actions: `ResetStagesAfterTarget`, `ActivateTargetStage`;
- emits: `approval.process.resumed`.

Переход `Recalled → Draft` (`ReworkFromRecalled`, P-S-07) в этом сценарии не используется и не
реализуется: полный перезапуск с первого этапа больше не является поведением по умолчанию.
Существующее описание «после отзыва — в Черновик для полного перезапуска»
(`business_context_v2.0.md` §14.2/§14.4) заменяется этим решением.

8.3. `ResetStagesAfterTargetAction` (`targetStageId`):

- этапы с `orderIdx > target.orderIdx`: статус → `Pending`; активные итерации → `Cancelled`;
  `startedAt`/`dueAt`/`completedAt` очищаются; история итераций сохраняется;
- этапы до цели (не включая) не меняются;
- цель обрабатывает `ActivateTargetStage`.

Запись статуса `Pending` — то же документированное исключение, что и `RejectStagesFrom`:
отдельного перехода «сброс в Pending» в карте нет. Если решено делать строго через переходы —
добавить `ResetStage` (`Approved`/`ApprovedWithComments`/`Completed`/`Cancelled`/`Rejected` →
`Pending`) и использовать его.

8.4. Повторная активация этапа (обязательно для возврата на более ранний этап):

- `ActivateNextStageAction` при активации этапа переиспользует последнюю итерацию, только если
  она в статусе `Pending` (первый проход); иначе создаёт новую итерацию и клонирует участников
  предыдущей (инвариант §7.4). Сейчас создания новой итерации нет — активация переиспользует
  последнюю итерацию, что для повторного прохода неверно;
- `ActivateTargetStageAction` — то же правило: `Pending`-итерацию переиспользовать, иначе
  создавать новую (сейчас всегда создаёт новую, из-за чего при отзыве из
  `PendingResponsibleApproval` начальная итерация остаётся неиспользованной);
- в карты `STAGE/STANDARD` и `STAGE/UNIFIED` добавить `ReactivateStage` из `Rejected`,
  `Cancelled` (для UNIFIED — также из `Completed`) в `Active`, чтобы этапы, сброшенные или
  отклонённые при возврате, могли быть пройдены заново.

8.5. `ResumeAfterRecall` доступен, только если политика `RECALL` не `DENY`. При `DENY` —
`409 RETURN_NOT_ALLOWED`; процесс остаётся `Recalled` до архивации.

8.6. Для `UNIFIED` источник при `AwaitingFinalDecision` — последний этап (см. §6); финальное
решение не записывается и остаётся ожидаемым.

8.7. Отзыв из `PendingResponsibleApproval` (UNIFIED, маршрут ещё не запущен): возобновление
возвращает в `PendingResponsibleApproval` отдельным переходом
`ResumeAfterRecallToResponsible` (guard `ProcessNotStarted`: `startedAt is null`), без активации
этапов; политика `RECALL` здесь не применяется — возвращаться к этапам ещё не от чего. Для
запущенного маршрута работает `ResumeAfterRecall` → `InProgress`.

### 9. API и DTO

9.1. Шаблон:

```json
{
  "stages": [
    {"key": "input_control", "name": "Входной контроль", "stageType": "APPROVAL", "...": "..."},
    {"key": "legal_review",  "name": "Юридическая проверка", "...": "..."},
    {"key": "signing",       "name": "Подписание", "...": "..."}
  ],
  "returnRules": [
    {"reason": "REWORK", "from": null, "mode": "FORCED",
     "targets": [{"stageKey": "input_control"}]},
    {"reason": "REWORK", "from": "signing", "mode": "CHOICE",
     "targets": [{"stageKey": "signing"},
                 {"stageKey": "input_control"}]},
    {"reason": "RECALL", "from": null, "mode": "FORCED",
     "targets": [{"stageKey": "input_control"}]}
  ]
}
```

`GET /templates/{id}` возвращает те же `key` и `returnRules`; для удобства UI — вычисленную
эффективную матрицу (можно отдельным полем `returnPolicyPreview`, необязательным).

9.2. Процесс:

`GET /api/v1/processes/{id}/return-options?reason=REWORK|RECALL`:

```json
{
  "reason": "REWORK",
  "fromStageId": "11111111-...",
  "mode": "FORCED",
  "targets": [
    {"stageId": "22222222-...", "key": "input_control", "name": "Входной контроль", "orderIdx": 1}
  ],
  "defaultTargetStageId": "22222222-..."
}
```

`mode: DENY` — `200` с пустым `targets` (UI скрывает кнопку); возобновление → `409`.

`POST /api/v1/processes/{id}/resume`:

```json
{"reason": "REWORK", "targetStageId": "22222222-...", "comment": "Замечания исправлены"}
```

- `reason` необязателен: `OnRework` → `REWORK`, `Recalled` → `RECALL`; несовпадение с статусом —
  `422 RETURN_REASON_MISMATCH`;
- `targetStageId` необязателен для `FORCED` (сервер подставит), обязателен для `CHOICE`;
- коды: `422 RETURN_TARGET_NOT_ALLOWED`, `409 RETURN_NOT_ALLOWED`, `409 RETURN_POLICY_MISSING`,
  `422 RETURN_REASON_MISMATCH`.

`POST /api/v1/processes/{id}/recall` — без изменения тела; в ответ добавить
`resumable: true|false` (по политике `RECALL`).

9.3. Обратная совместимость: `allowedReturnStages` в запросе шаблона можно принимать как
deprecated alias и конвертировать в правило `(REWORK, from=этап, CHOICE, targets)`; в ответе не
отдавать. Удалить alias после миграции клиентов.

Если `returnRules` не передан вовсе (старый клиент), сервис создаёт **видимые** правила по
умолчанию, сохраняя прежнее поведение: для каждого этапа `(REWORK, from=этап, FORCED,
targets=[этот этап])` и одно `(RECALL, null, DENY)`. Они сохраняются как обычные правила,
отдаются в `GET /templates/{id}` и могут быть изменены администратором; неявных правил в
рантайме нет.

### 10. Валидация при публикации

`ValidateTemplateStructureAction` вызывает `ReturnPolicyCompiler` и дополнительно проверяет:

1. ключи этапов: непустые, уникальные, соответствуют формату; в опубликованной версии не
   меняются;
2. `from_stage_key` и `stage_key` целей существуют в шаблоне;
3. `mode`/количество целей: `FORCED` = 1, `CHOICE` ≥ 1, `DENY` = 0; дублей нет;
4. запрет возврата «вперёд»: для каждого правила и каждого этапа, к которому оно применимо,
   `target.orderIdx <= stage.orderIdx`;
5. полнота: для каждой пары «этап × причина, применимая к типу процесса» есть правило (или
   `DENY`); иначе ошибка со списком непокрытых этапов;
6. `REWORK` не используется для `UNIFIED`; `DENY` — только для `RECALL`;
7. `when_condition` в v1 обязан быть `null`; непустое значение — ошибка
   `RETURN_RULE_CONDITIONS_NOT_SUPPORTED`;
8. причина существует и активна в `return_reason_registry`.

Ошибки — `IllegalArgumentException` с путём до правила/этапа; публикация откатывается.

### 11. Миграция (V26 + V27)

V26:

1. добавить `stage_template.key`, заполнить `stage_<order_idx>`, `not null`, уникальный индекс;
2. создать таблицы из §2, засеять причины;
3. для каждого этапа с непустым `allowed_return_stages` создать правило
   `(REWORK, from=этот этап, CHOICE, targets=этапы с указанными orderIdx)`;
4. для каждого этапа без явного правила создать `(REWORK, from=этап, FORCED, targets=[этот
   этап])`, чтобы сохранить старое поведение «только текущий этап»;
5. для каждого шаблона создать `(RECALL, null, DENY)` — старое поведение «возобновления после
   отзыва нет»;
6. для существующих процессов собрать `return_policy` из
   `stage_instance.allowed_return_stages` (для каждого этапа: непустой список → `CHOICE` с
   целями, пустой/null → `FORCED` на сам этап; `RECALL` → `DENY`);
7. удалить `stage_template.allowed_return_stages` и `stage_instance.allowed_return_stages`.

V27: переходы, guards, actions:

- `guard_registry`: `IsReturnTargetAllowed`; деактивировать `IsTargetStageAllowed`;
- `action_registry`: `CancelProcessWork`, `ResetStagesAfterTarget`;
- `status_registry`: `Recalled` → `is_terminal = false` (реестр не должен противоречить
  возобновлению; движок флаг не читает, но реестр — источник истины для UI);
- `PROCESS/STANDARD`, `PROCESS/UNIFIED`: `ResumeAfterRecall` (`Recalled → InProgress`);
  `PROCESS/UNIFIED`: `ResumeAfterRecallToResponsible` (`Recalled → PendingResponsibleApproval`);
  `RecallProcess` с `CancelProcessWork` (для UNIFIED — из `PendingResponsibleApproval`,
  `InProgress`, `AwaitingFinalDecision`);
- `STAGE/STANDARD`, `STAGE/UNIFIED`: `ReactivateStage` из `Rejected`/`Cancelled`
  (для UNIFIED — также `Completed`);
- обновить guards перехода `ResumeProcess`: `IsReturnTargetAllowed` вместо
  `IsTargetStageAllowed` (actions не меняются).

### 12. Условия `when` (этап 2, отдельный PR)

Колонка `when_condition` уже есть. В v1 — только `null`. При включении:

- контекст условия (read-only, на момент возврата): `process.processType`, `process.status`,
  `entity.attributes.*` (снимок), `stage.key`, `stage.orderIdx`, `stage.attempt` (номер последней
  итерации), `remarks.active/inProgress/resolved/rejected/total`,
  `decisions.approve/approveWithComments/reject`;
- операторы: `eq`, `neq`, `in`, `gt`, `gte`, `lt`, `lte`, `exists`; логика — неявное `AND` по
  полям, при необходимости `all`/`any`;
- правила для пары «этап × причина» упорядочены по `priority`; побеждает первое с истинным
  условием; последнее правило обязано быть безусловным (иначе ошибка публикации);
- вынести/переиспользовать оценщик условий из `MatchService` (`attributeMatches`) в общий
  компонент с поддержкой точечных путей;
- снимок уже хранит список записей — при включении условий в нём появляются несколько
  кандидатов на пару с `priority` и `when`;
- тесты: условие по числу отклонённых замечаний, по атрибуту сущности, отсутствие подходящего
  правила → ошибка публикации.

### 13. Права

Политику редактирует только администратор шаблона — через существующие операции
создания/обновления/публикации шаблона. Отдельного API редактирования политики процесса нет.
Если в модуле ещё нет аутентификации, зафиксировать это как существующее ограничение (actorId
приходит из тела запроса), не расширяя объём тикета.

## Критерии приёмки

1. Шаблон с правилом `REWORK → input_control` (FORCED): отклонение на подписании → процесс
   `OnRework` → `POST /resume` без `targetStageId` возвращает на «Входной контроль»; в
   `audit_event.payload` есть `reason`, `ruleId`, `targetStageId`.
2. Попытка вернуть на запрещённый или более поздний этап → `422 RETURN_TARGET_NOT_ALLOWED`,
   процесс остаётся `OnRework`, статусы этапов не меняются.
3. `CHOICE` с двумя целями: `GET /return-options` отдаёт `mode=CHOICE` и две цели; возобновление
   на любую из них проходит; на третью — `422`.
4. `DENY` для `RECALL`: `GET /return-options` отдаёт `mode=DENY`; `POST /resume` → `409
   RETURN_NOT_ALLOWED`.
5. Отзыв: `InProgress → Recalled` отменяет активный этап/итерацию/задачи, маршрут сохраняется
   (те же `stageId`); `POST /resume` с `reason=RECALL` возвращает на цель правила, этапы после
   цели — `Pending`, этапы до цели не меняются, у цели новая итерация.
6. `UNIFIED`: отзыв из `AwaitingFinalDecision` сохраняет маршрут и возобновляется по правилу
   `RECALL`; финальное решение остаётся не записанным. Отзыв из `PendingResponsibleApproval`
   возвращает в `PendingResponsibleApproval` (`ResumeAfterRecallToResponsible`), этапы не
   активируются.
7. Публикация шаблона отклоняется с понятной ошибкой, если: цель позже источника; есть
   непокрытая пара «этап × причина»; ключ отсутствует/дублируется; `FORCED` с двумя целями;
   `REWORK` у `UNIFIED`; `DENY` у `REWORK`; `when` непустой в v1.
8. Миграция: шаблон с `allowedReturnStages=[1,2]` после миграции ведёт себя так же; существующий
   процесс с `stage_instance.allowed_return_stages` после миграции использует эти цели (ранее
   guard их игнорировал — поведение исправляется).
9. `./gradlew build` зелёный; обновлены/добавлены тесты из §«Тестирование».

## Тестирование

**Unit:**

- `ReturnPolicyCompilerTest` — приоритет правила для конкретного этапа над правилом по
  умолчанию; конкретные цели (включая «на себя» по ключу самого этапа); запрет «вперёд»;
  покрытие; режимы и количество целей; `REWORK`/`UNIFIED`.
- `ReturnPolicyServiceTest` — выбор источника (`OnRework`, `Active`, последний, первый);
  `FORCED`/`CHOICE`/`DENY`; неизвестная цель; снимок без записи.
- `ValidateTemplateStructureActionTest` — новые ошибки публикации.
- `ResetStagesAfterTargetActionTest` — сброс этапов/итераций, история сохраняется.

**Интеграционные (Testcontainers):**

- `ProcessResumeIntegrationTest` — возврат по `FORCED` без цели; `CHOICE`; запрещённая цель;
  `DENY`.
- `ProcessRecallIntegrationTest` — отзыв из `InProgress`/`OnRework`/`AwaitingFinalDecision`;
  возобновление после отзыва; сохранение маршрута; сброс этапов; новая итерация.
- `ProcessApiIntegrationTest` — коды `200/409/422`, тело `return-options`.
- `TemplateLifecycleIntegrationTest` — публикация с политикой, ошибки, ключи, версия/форк.
- `RouteGenerationServiceTest` — снимок политики в процессе, резолв ключей в `stageInstanceId`.
- `ReturnPolicyMigrationTest` (или проверка в интеграционном тесте) — backfill старых
  шаблонов/процессов.

**Регрессия:**

- `ActivateNextStageActionTest` / сценарий повторной активации этапа после возврата;
- `RejectStagesFromActionTest` — переход на `targetStageId`;
- сценарии `doc/arch/12_test_scenarios.md` группа 7 (отзыв и возобновление) и группа 3
  (возврат на доработку).

## Открытые вопросы

1. Требовать ли `AllRemarksProcessed` при возобновлении после **отзыва** (сейчас предполагается
   «да», как при доработке)? Если нет — убрать guard только для `RECALL`.
2. `DENY` для `RECALL` означает «процесс остаётся отозванным до архивации»; подтвердить, что
   другого выхода (например, принудительный перезапуск администратором) не требуется.
3. Условия `when`: контекст и операторы из §12; отдельно решить, нужна ли «серьёзность
   замечания» — в модели `Remark` такого поля нет, потребуется добавить или обойтись
   количеством/ролями.
4. Редактирование маршрута после отзыва: в текущем API его нет; если появится — снимок политики
   перекомпилируется по ключам, исчезновение ключа блокирует сохранение. Подтвердить, что это
   отдельная фаза.
5. Нужен ли `GET /templates/{id}/return-policy` (эффективная матрица) отдельно или достаточно
   поля в `GET /templates/{id}`?
