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
| Gradle | 8.8 |
| Движок машины состояний | собственная реализация (`TransitionEngine`, ADR-028) |

## Статус

**Фаза 1 — фундамент.** Реализовано:

- Каркас Gradle-проекта (build.gradle, wrapper 8.8).
- Пакетная структура (`ru.coordination.approval`).
- Доменные сущности (JPA) всех 5 агрегатов по `03_domain_model.md`:
  RegistryAggregate, TemplateAggregate, ProcessAggregate, StateMachineAggregate,
  AuditAggregate — плюс `guard_registry`/`action_registry`/`notification_settings`
  как простые 1:1-отображения таблиц.
- Полная схема БД (Flyway, 27 таблиц / 17 групп) по `08_db_schema.md`, отражающая
  ADR-028 (без `ssm_state_machine_context`).
- Базовая конфигурация (`application.yml`, Shedlock/планировщик, `docker-compose.yml`
  для локального Postgres + RabbitMQ).

**Не реализовано** (следующие фазы): `TransitionEngine` и остальные компоненты движка
(`ModelFactory`, `ComponentResolver`, `GuardRegistry`, `ActionRegistry` — исполняемая
логика, не только реестр в БД), guards/actions, адаптеры (`RoleResolverAdapter`,
`EntityAdapter`, `KripAdapter`), REST API (`07_api_contract.md`), публикация событий
(Outbox → RabbitMQ), джобы планировщика (автоархивация, напоминания, идемпотентность).

Таблицы `idempotency_key`, `outbox_event` и `shedlock` пока не имеют JPA-сущностей —
`shedlock` управляется самой библиотекой Shedlock, а `idempotency_key`/`outbox_event`
предполагается обслуживать через `JdbcTemplate`/нативные запросы в следующих фазах
(будет уточнено при реализации соответствующих компонентов).

## Локальный запуск

```bash
docker compose up -d        # Postgres + RabbitMQ
./gradlew bootRun           # требует сеть для загрузки Gradle 8.8 и зависимостей
```

> В среде, где готовился этот каркас (облачная песочница), исходящий доступ к
> Maven Central и Gradle Plugin Portal был заблокирован политикой egress, поэтому
> `./gradlew build` там не выполнялся. Структура и корректность соответствия
> `@Column`/`@JoinColumn` ↔ DDL-миграциям проверены статически (скриптом, сверяющим
> имена колонок), но **полная сборка с резолвом зависимостей ещё не подтверждена** —
> первым шагом после клонирования стоит прогнать `./gradlew build` в CI или локально.

## Документация

Спецификации проекта — в Claude Project "coordination-processing-module"
(`00_vision_scope.md` … `12_test_scenarios.md`, ADR в `11_adr.md`).
