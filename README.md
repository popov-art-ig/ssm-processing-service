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

**Не реализовано** (следующие фазы): `StageService`/`StageStateMachine` и переход
`ActivateStage`, остальные переходы `ProcessStateMachine` после `StartProcess`,
`MatchService`/`RouteGeneratorService`/`RouteValidatorService` (создание процесса из
шаблона), адаптеры (`RoleResolverAdapter`, `EntityAdapter`, `KripAdapter`), REST API
(`07_api_contract.md`), публикация событий (Outbox → RabbitMQ — `TransitionEngine` уже
отдаёт `emits` в `TransitionResult`, публикатора пока нет), джобы планировщика
(автоархивация, напоминания, идемпотентность), тип процесса `UNIFIED`.

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
