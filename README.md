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

**Не реализовано** (следующие фазы): `MatchService`/`RouteGeneratorService`/
`RouteValidatorService` (создание процесса из шаблона), адаптеры (`RoleResolverAdapter`,
`EntityAdapter`, `KripAdapter`), REST API (`07_api_contract.md`), публикация событий
(Outbox → RabbitMQ — `TransitionEngine` уже отдаёт `emits` в `TransitionResult`, публикатора
пока нет), джобы планировщика (автоархивация, напоминания, идемпотентность), тип процесса
`UNIFIED`, дополнительные согласующие (PHASE-10), уведомления (PHASE-12), реакции на события
(PHASE-13).

Таблицы `idempotency_key`, `outbox_event` и `shedlock` пока не имеют JPA-сущностей —
`shedlock` управляется самой библиотекой Shedlock, а `idempotency_key`/`outbox_event`
предполагается обслуживать через `JdbcTemplate`/нативные запросы в следующих фазах
(будет уточнено при реализации соответствующих компонентов).

## Локальный запуск

```bash
docker compose up -d        # Postgres + RabbitMQ
./gradlew bootRun           # профиль по умолчанию; ./gradlew bootRun --args='--spring.profiles.active=local' для show-sql
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

`./gradlew build` (включая полный тестовый набор — юнит- и Testcontainers-интеграционные
тесты, требует Docker) проверен и зелёный локально на Windows.

## Документация

Спецификации проекта — в Claude Project "coordination-processing-module"
(`00_vision_scope.md` … `12_test_scenarios.md`, ADR в `11_adr.md`). Постановки фаз для
исполнения в этом репозитории (самодостаточные, без внешних ссылок) — `doc/tickets/`;
процесс подготовки и приёмки тикетов — `doc/tickets/TEMPLATE.md`.
