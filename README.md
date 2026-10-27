# ssm-processing-service

Модуль «Согласование» (coordination-processing-module) — сервис координации процессов
согласования документов.

> Название репозитория унаследовано от исходной архитектуры (Spring State Machine).
> С ADR-028 движок машины состояний — собственная реализация; переименование
> репозитория опционально и в объём Фазы 1 не входит.

## Стек

Зафиксирован в `11_adr.md` (ADR-028, 2026-09-18):

| Компонент | Версия |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.3 |
| Spring Cloud | 2025.1.1 ("Oakwood") |
| Hibernate | 7.4.0 |
| Gradle | 8.14 |
| Testcontainers | 1.21.4 |
| Движок машины состояний | собственная реализация (`TransitionEngine`, ADR-028) |

## Статус

Подробная постановка и критерии приёмки — по фазам в `doc/tickets/PHASE-NN-*.md`;
разбивка на задачи и обоснование конкретных решений — в `doc/tasks/PHASE-NN-tasks.md`.
Здесь — сводка.

**Фаза 1 — фундамент.** Готово: каркас Gradle-проекта, доменные сущности (JPA) всех
5 агрегатов по `03_domain_model.md`, полная схема БД (Flyway, 27 таблиц / 17 групп,
`08_db_schema.md`), базовая конфигурация (`application.yml`, Shedlock/планировщик,
`docker-compose.yml` для локального Postgres + RabbitMQ).

**Фаза 2 — State Machine Engine.** Готово: `TransitionEngine` (пакет `engine`) —
собственная реализация вместо архивированного Spring State Machine (ADR-028):
`ModelFactory` (in-memory модель переходов по конкретной версии конфига),
`GuardRegistry`/`ActionRegistry` + `ComponentResolver` (резолвинг `guard_registry`/
`action_registry.handler` в Spring-бины), интерфейсы `Guard`/`Action`. Без конкретных
guard/action-бинов бизнес-логики и без публикации событий — только механизм.

**Фаза 3 — ProcessService: StartProcess.** Готово: первый сквозной путь через
`TransitionEngine` — переход `StartProcess` (`Draft → InProgress`, `STANDARD`,
`04_state_machines.md` §4.2). `ProcessService` (пакет `service`), три guard-бина
(`IsInitiator`, `AllMandatorySlotsFilled` — упрощённая per-stage реализация,
`AllDurationsValid`) и один action-бин (`SetStartedAt`); миграция `V18` сеет
соответствующие строки `guard_registry`/`action_registry`/`status_registry`/
`state_machine_config`. Без создания процесса «с нуля» (`MatchService`/
`RouteGeneratorService`) и без активации этапов (`StageService`).

**Фаза 4 — активация первого этапа.** Готово: `StartProcess` теперь по-настоящему активирует
маршрут — action `AssignStageTasks` вызывает вложенный переход `ActivateStage`
(`Pending → Active`, минимальный `StageStateMachine`, `04_state_machines.md` §5.1–5.2) в той
же транзакции. Guard `PreviousStageCompleted` (для первого этапа — всегда `true`) и три
action-бина уровня этапа (`AssignParticipantTasks` — `Parallel`/`Sequential`,
`SetStageStartedAt`, `CalcStageDueAt` — единица `duration` принята как дни); миграция `V19`.
Без создания новой `StageIteration` при активации и без решений участников (`Decide`,
`ParticipantStateMachine`) — только сама активация.

**Фаза 5 — решения участников (Decide).** Готово: `ParticipantStateMachine` с переходами
`StartDecision`, `Decide` (Approve/Reject/ApproveWithComments), guard-бины
(`IsAssignedParticipant`, `IsValidDecisionType`, `HasCommentWhenRequired`), action-бины
(`RecordDecision`, `SetDecidedAt`, `RecordComment` — запись в таблицу `comment`); миграция
`V20`. Метод `DecisionService.decide()` обрабатывает решения участников, но агрегация решений
на уровне этапа (`StageStateMachine` переходы `StageApproved`/`StageOnRework`) — следующая фаза.

**Фаза 6 — агрегация решений и закрытие этапа.** Готово: guard-бины
(`AllParticipantsDecided`, `AggregationApproved`, `AggregationApprovedWithComments`,
`AggregationRework`) реализуют логику подсчёта голосов по `decisionMode` (ALL/ANY/MAJORITY),
action-бин `SetStageCompletedAt` фиксирует время закрытия; миграция `V21` добавляет три
перехода `StageStateMachine` (`StageApproved`, `StageApprovedWithComments`, `StageOnRework`).
Этап теперь автоматически меняет статус после того, как все участники приняли решение.

**Фаза 7 — завершение процесса и активация следующего этапа.** Готово: guard-бины
(`AllStagesCompleted`, `HasComments`), action-бины (`ActivateNextStage`, `CompleteProcess`,
`EvaluateProcessCompletion`); миграция `V22` добавляет переходы `ProcessStateMachine`
(`ProcessApproved`, `ProcessApprovedWithComments`) и обновляет переходы `StageApproved`/
`StageApprovedWithComments` добавлением actions для активации следующего этапа и проверки
завершённости процесса. Процесс автоматически завершается, когда все этапы согласованы.

**Фаза 8 — возврат на доработку и возобновление процесса.** Готово: guard-бины
(`HasStageOnRework`, `AllRemarksProcessedGuard` — заглушка, `IsTargetStageAllowedGuard` —
упрощённая реализация), action-бины (`EvaluateProcessReworkAction`, `RejectStagesFromAction`,
`ActivateTargetStageAction`); миграция `V23` добавляет переходы `ProcessRework`
(InProgress → OnRework), `ResumeProcess` (OnRework → InProgress), `ReactivateStage`
(OnRework → Active для этапов), статусы `OnRework` (PROCESS) и `Rejected` (STAGE).
Метод `ProcessService.resumeProcess()` позволяет инициатору вернуть процесс на указанный
этап после доработки замечаний.

**Фаза 11 — замечания и комментарии (Remark lifecycle).** Готово: `RemarkService` с методами
`createRemark()`, `processRemark()`, `rejectRemark()`; guard-бины (`IsParticipant` — заглушка,
`IsRemarkAuthor`, `IsRemarkAssignee`, `HasUnprocessedRemarks`), action-бины
(`AssignRemarkToAuthor`, `NotifyRemarkStatusChange` — заглушка); миграция `V24` добавляет
`RemarkStateMachine` с переходами `CreateRemark`, `StartProcessingRemark`, `ProcessRemark`,
`RejectRemark` и статусами Draft/Open/InProgress/Processed/Rejected. Обновлён guard
`AllRemarksProcessedGuard` из фазы 8 для реальной проверки наличия необработанных замечаний.

**Фаза 14 — управление шаблонами (Template Management).** Готово: `TemplateService` с методами
`create()`, `publish()`, `deprecate()`, `archive()`; guard-бины (`AllMandatorySlotsValidGuard`,
`NoActiveProcessesGuard`), action-бин (`ValidateTemplateStructureAction` — валидация структуры
шаблона: обязательность `executionOrder`/`decisionMode` для STANDARD, запрет для UNIFIED,
валидация `allowedReturnStages`, проверка `stageTemplate` у слотов); миграция `V25` добавляет
`TemplateStateMachine` с переходами `CreateTemplate`, `PublishTemplate`, `DeprecateTemplate`,
`ArchiveTemplate` и статусами Draft/Published/Deprecated/Archived. Шаблоны теперь имеют полный
жизненный цикл с валидацией перед публикацией и защитой от изменений активных шаблонов.

**REST API (`07_api_contract.md`, PHASE-21-25).** Реализованы: процессы (создание, запуск,
отзыв, возобновление, список, детали), решения, шаблоны (полный жизненный цикл), агрегированные
представления для UI и административное чтение (карты переходов + их валидация, реестры
guard/action). Полный список эндпоинтов — `doc/REST_API_COMPLETION.md`.

**Не реализовано в REST API — 7 эндпоинтов-заглушек** (отвечают 500, бросают
`UnsupportedOperationException`):

| Контроллер | Эндпоинты |
|---|---|
| `RemarkController` | `POST /remarks`, `PUT /{id}/resolve`, `/{id}/reject`, `/{id}/accept`, `/{id}/reject-resolution` |
| `CommentController` | `POST /comments` |
| `AdminController` | `POST /admin/processes/{id}/restore` |

Сервисный слой при этом написан (`RemarkService`) — не хватает привязки контроллеров.

### Шаблоны: жизненный цикл

| Метод | Эндпоинт | Условие |
|---|---|---|
| POST | `/api/v1/admin/templates` | создаёт `DRAFT`, version 1 |
| PUT | `/api/v1/admin/templates/{id}` | `DRAFT` правится на месте; `PUBLISHED` создаёт **новую версию** (ADR-023) и снимает прежнюю с публикации |
| POST | `/api/v1/admin/templates/{id}/publish` | `DRAFT → PUBLISHED`, движок проверяет структуру |
| POST | `/api/v1/admin/templates/{id}/deprecate` | `PUBLISHED → DEPRECATED` |
| POST | `/api/v1/admin/templates/{id}/archive` | `DEPRECATED → ARCHIVED`, guard `NoActiveProcesses` |
| POST | `/api/v1/admin/templates/{id}/fork` | новая строка `version + 1` + `parentTemplateId`, исходная не меняется |
| GET | `/api/v1/admin/templates` | фильтры `status`, `processType`, `name` |

Методы жизненного цикла проверяют **предусловие по статусу**: без него движок выбирает переход
по текущему состоянию, и вызов «опубликовать» на уже опубликованном шаблоне молча выполнял
депрекацию вместо отказа.

Сквозной сценарий проверен в рантайме: создать шаблон → опубликовать → создать процесс
(автоподбор шаблона по правилам применимости) → запустить → принять решение → процесс
`Approved` с заполненным `completedAt`.

**Что действительно не реализовано** (следующие фазы): публикация событий (Outbox → RabbitMQ —
`TransitionEngine` уже отдаёт `emits` в `TransitionResult`, публикатора нет), джобы планировщика
(автоархивация, напоминания, идемпотентность), тип процесса `UNIFIED`, дополнительные
согласующие (PHASE-10), уведомления (PHASE-12), реакции на события (PHASE-13),
аутентификация/авторизация (JWT, RBAC — `07_api_contract.md` §5).

Таблицы `idempotency_key`, `outbox_event` и `shedlock` пока не имеют JPA-сущностей —
`shedlock` управляется самой библиотекой Shedlock, а `idempotency_key`/`outbox_event`
предполагается обслуживать через `JdbcTemplate`/нативные запросы в следующих фазах
(будет уточнено при реализации соответствующих компонентов).

## Локальный запуск

```bash
docker compose up -d        # Postgres + RabbitMQ
./gradlew bootRun           # профиль по умолчанию; ./gradlew bootRun --args='--spring.profiles.active=local' для show-sql
```

Postgres в compose публикуется на хосте как **5433**: порт 5432 обычно занят нативным
PostgreSQL (служба `postgresql-x64-18`), и подключения к `localhost:5432` уходят в него,
а не в контейнер. Поэтому при запуске приложения вне Docker (в том числе `./gradlew bootRun`)
адрес БД нужно задавать явно:

```bash
DB_URL=jdbc:postgresql://localhost:5433/coordination ./gradlew bootRun
# PowerShell: $env:DB_URL='jdbc:postgresql://localhost:5433/coordination'; ./gradlew bootRun
```

Параметры для внешних клиентов (DBeaver, psql, Database в IDEA):

| Параметр | Значение |
|---|---|
| Host | `localhost` |
| Port | `5433` |
| Database | `coordination` |
| User / Password | `coordination` / `coordination` |

Внутри docker-сети порт не менялся: приложение и RabbitMQ по-прежнему видят БД как `postgres:5432`.

Проверено в рантайме (2026-10-10): на чистой БД Flyway применяет 25 миграций, стартовая
валидация карт переходов проходит, сервис отвечает на `/actuator/health` (`UP`).

### Запуск целиком в Docker

```bash
docker compose up -d --build   # Postgres + RabbitMQ + приложение на :8080
```

`Dockerfile` упаковывает **уже собранный** jar (`./gradlew bootJar`), а не собирает проект
внутри образа: стадия `gradle build` в контейнере требует доступа к `repo.maven.apache.org`,
который в этом окружении не проходит TLS-handshake. Порядок: сначала `./gradlew bootJar`,
затем `docker compose up -d --build`.

Продакшн-вариант без Dockerfile — buildpacks-сборщик Spring Boot:

```bash
./gradlew bootBuildImage --imageName=ssm-processing-service:local
```

Если база уже создавалась предыдущими версиями миграций, её нужно пересоздать: набор
миграций `V1`–`V25` менялся (составной ключ `status_registry`, уникальность переходов по
`(config_id, code, from_state)`, приоритеты переходов завершения, `is_initial` у
`PARTICIPANT.Assigned`, имена событий), и Flyway отклонит изменившиеся контрольные суммы:

```bash
docker compose down -v && docker compose up -d
```

Полностью в Docker (без локального JDK) — через встроенный в Spring Boot Gradle-плагин
buildpacks-сборщик образа, без Dockerfile:

```bash
docker compose up -d
./gradlew bootBuildImage --imageName=ssm-processing-service:local
docker run -d --name ssm-processing-service --network <project>_default \
  -e DB_URL=jdbc:postgresql://postgres:5432/coordination \
  -e DB_USERNAME=coordination -e DB_PASSWORD=coordination \
  -e RABBITMQ_HOST=rabbitmq -p 8080:8080 ssm-processing-service:local
```

## Сборка и проверки

```bash
./gradlew verifyTestCompilation   # быстрая проверка компиляции main+test, Docker не нужен
./gradlew build                   # полный набор: юнит- и Testcontainers-интеграционные тесты
```

`./gradlew build` проверен и зелёный (298 тестов, 51 класс, Docker обязателен для
интеграционных тестов на Testcontainers).

**Нагрузочные тесты.** Gatling 3.16 (Java DSL) в source set `src/gatling/java`, запуск отдельной
задачей — в `./gradlew build` не входит, потому что генератору нужен уже поднятый сервис:

```bash
./gradlew gatlingRun --simulation=ru.coordination.approval.loadtest.SeedSimulation   # данные
./gradlew gatlingRun --simulation=ru.coordination.approval.loadtest.UiReadSimulation # профиль чтения (разгон)
./gradlew gatlingRun --simulation=ru.coordination.approval.loadtest.LifecycleSimulation
./gradlew gatlingRun --simulation=ru.coordination.approval.loadtest.TemplateLifecycleSimulation
```

SLO проверяется одной задачей: она создаёт набор, на котором зафиксированы пороги, и падает, если
p95/p99 нарушены. Эта же задача используется ночным гейтом в CI
(`.github/workflows/performance.yml`):

```bash
./gradlew loadTest               # сид + SLO-полка 40 польз./с против http://localhost:8080
./gradlew loadTest -x loadSeed   # повторный прогон по уже созданным данным
```

Постановка, профили нагрузки, SLO, разбор результата и очистка данных — `doc/LOAD_TESTING.md`.
**Компиляция тестов обязательна.** Задача `verifyTestCompilation` выделяет
`:compileTestJava` в отдельный адресуемый шаг — его нужно запускать в CI первым. Причина:
ранее в репозиторий попали тестовые классы, которые не компилировались (ссылались на
несуществующие сервисы, удалённые в Spring Boot 4.0 аннотации `@WebMvcTest`/`@MockBean` и
несуществующие методы доменных билдеров), из-за чего `./gradlew build` падал ещё до запуска
тестов, а тесты не выполнялись вовсе.

## Комментарии в коде

Соглашение о комментариях — `doc/CODE_COMMENTS.md`; отчёт о покрытии и найденных дефектах —
`doc/JAVADOC_COVERAGE.md`. Кратко: Javadoc обязателен для типов, публичных методов и
неочевидных полей, ценность комментария — в ответе на вопрос «почему» (порядок операций,
выбор статуса, отклонение от спецификации), а ссылки даются на разделы `doc/arch/*.md` и
`doc/tickets/PHASE-*.md`.

```powershell
# доля задокументированных типов/методов/полей (-Details, -ByPackage, -ListMissingTypes)
powershell -ExecutionPolicy Bypass -File doc/tools/CommentCoverage.ps1
# доказательство, что diff меняет только комментарии
powershell -ExecutionPolicy Bypass -File doc/tools/VerifyCommentsOnly.ps1 -Base main
# проверка, что все {@link} указывают на существующие типы и члены
powershell -ExecutionPolicy Bypass -File doc/tools/VerifyJavadocLinks.ps1
# компиляция main (+ тестов с -WithTests) без Gradle, сети и Docker
powershell -ExecutionPolicy Bypass -File doc/tools/CompileCheck.ps1 -WithTests
```

## Валидация карты переходов

Карта переходов (`transition_config` + `state_config` + реестры guard/action) проверяется
до того, как начать исполняться (`05_guards_actions_registry.md` §7):

```bash
curl -X POST http://localhost:8080/api/v1/admin/state-machines/{configId}/validate
```

| Проверка | Код нарушения |
|---|---|
| Guard зарегистрирован и активен | `GUARD_NOT_FOUND` |
| Action зарегистрирован и активен | `ACTION_NOT_FOUND` |
| Событие присутствует в контракте `06_event_contract.md` | `EVENT_UNKNOWN` |
| `from_state`/`to_state` существуют в карте | `STATE_UNKNOWN` |
| Ровно одно начальное состояние | `INITIAL_STATE_MISSING`, `MULTIPLE_INITIAL_STATES` |
| Код перехода уникален для `from_state` | `DUPLICATE_TRANSITION_CODE` |
| Из `(from_state, trigger)` нет двух исходов с одинаковыми guards | `AMBIGUOUS_TRANSITION` |
| Guard/action применим к типу сущности карты | `APPLICABILITY_MISMATCH` |
| Параметры соответствуют `params_schema` | `PARAMS_INVALID` (схемы пока не заполняются) |

Та же проверка выполняется **при старте приложения** для всех карт в статусе `PUBLISHED`:
некорректная опубликованная карта не даст сервису подняться — вместо молчаливого отказа
во время перехода. Черновики (`DRAFT`) не проверяются: именно валидация должна не пустить
их в `PUBLISHED`.

`AMBIGUOUS_TRANSITION` сравнивает наборы guards. Разные guards из одной пары
`(from_state, trigger)` — осознанная конфигурация (например `ProcessApprovedWithComments`
с `HasComments` проверяется раньше обычного `ProcessApproved` благодаря приоритету). Одинаковые
же guards при разных `to_state` означают, что исход выбирается по `priority` и
лексикографическому порядку кодов, то есть случайно — это и есть дефект.

## Тестовое окружение

Профиль `test` описан в `src/test/resources/application-test.yml`: Testcontainers-Postgres
(`jdbc:tc:...`) и локации Flyway `classpath:db/migration` + `classpath:db/test-migration`
(последняя добавляет таблицу `engine_test_target`, нужную тестовым сущностям движка).

## Документация

Спецификации проекта — в Claude Project "coordination-processing-module"
(`00_vision_scope.md` … `12_test_scenarios.md`, ADR в `11_adr.md`). Постановки фаз для
исполнения в этом репозитории (самодостаточные, без внешних ссылок) — `doc/tickets/`;
процесс подготовки и приёмки тикетов — `doc/tickets/TEMPLATE.md`.

Соглашение о комментариях в коде — `doc/CODE_COMMENTS.md`; отчёт о покрытии и о дефектах,
зафиксированных в комментариях, — `doc/JAVADOC_COVERAGE.md`. Найденные расхождения кода со
спецификацией разложены в тикеты — `doc/tickets/DEFECTS-INDEX.md` (DEFECT-01…20).

Сводная матрица всех статусов и переходов (целевая карта и фактическая реализация) —
`doc/arch/13_status_transition_matrix.md`. Сценарная версия той же карты (от действия пользователя
к сущностям и переходам) — `doc/arch/14_scenario_transition_matrix.md`.
