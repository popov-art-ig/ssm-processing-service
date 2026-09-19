# PHASE-03 — разбивка на задачи

Источник: [`doc/tickets/PHASE-03-process-service-start.md`](../tickets/PHASE-03-process-service-start.md).
Реализация — одна ветка `feature/phase-3-process-service-start`, один PR в `main`.

Статусы: `Не начато | В работе | Готово`.

## T1. Миграция V18 (реестры + конфиг StartProcess) — Готово

`src/main/resources/db/migration/V18__seed_start_process_transition.sql` — вставки в
`guard_registry`/`action_registry`/`status_registry`/`state_machine_config`/`state_config`/
`transition_config` практически дословно из тикета (детерминированные UUID из тикета — так
проще узнавать их в тестах).

## T2. `StateMachineConfigRepository.findByEntityTypeAndProcessTypeAndVersion` — Готово

Добавлен derived-query метод (Фаза 2 репозиторий, `engine/model`) — `ProcessService`
резолвит `configId` по бизнес-версии конфига, а не по UUID напрямую.

## T3. Пакеты — решение по открытому вопросу №1 — Готово

- `ProcessRepository` → `ru.coordination.approval.domain.process` (рядом с `ProcessInstance`,
  а не в `service`): это репозиторий агрегата, а не внутренний инструмент движка/сервиса —
  в отличие от репозиториев Фазы 2 (`engine/model`, `engine/registry`), которые существуют
  только для обслуживания `ModelFactory`/`GuardRegistry`, `ProcessInstance` — доменная
  сущность, которую в будущих фазах наверняка будут запрашивать и другие сервисы напрямую.
  `StageInstance`/`StageIteration`/`Participant` — без отдельных репозиториев, сохраняются
  каскадом (`cascade = ALL`) через `ProcessRepository.save(process)`, как и написано в
  тикете («эта фаза добавляет минимум необходимое»).
- `ProcessService` → `ru.coordination.approval.service` (новый top-level пакет, как и
  требовал тикет).
- Guard/action-бины → `ru.coordination.approval.service.guard` /
  `ru.coordination.approval.service.action` — по аналогии с `engine.registry` Фазы 2;
  соглашение, которое Фаза 4 (`StageService`) может продолжить теми же двумя пакетами.

## T4. Guards — Готово

`IsInitiatorGuard`, `AllMandatorySlotsFilledGuard`, `AllDurationsValidGuard`
(`service.guard`, бины `isInitiatorGuard`/`allMandatorySlotsFilledGuard`/
`allDurationsValidGuard` — имена совпадают с `handler` в миграции V18). Приводят
`context.entity()` к `ProcessInstance` внутри бина (контракт `TransitionContext.entity` —
`Object`, не меняется).

`AllMandatorySlotsFilledGuard` — упрощённая per-stage версия из тикета (раздел 2, «Упрощение...
важно, отличается от буквальной спеки»): по каждому `mandatory`-этапу берётся итерация с
максимальным `iterationIdx`, проверяется что она существует и её `participants` не пуст.

## T5. Action — Готово

`SetStartedAtAction` (`service.action`, бин `setStartedAtAction`) — `process.setStartedAt
(Instant.now())`.

## T6. `ProcessRepository` + `ProcessService.startProcess` — Готово

`ProcessService.startProcess(UUID processId, UUID actorId)`: загружает процесс, резолвит
`configId` через T2, собирает `TransitionContext(process, actorId, ActorType.USER, Map.of())`,
вызывает `transitionEngine.transition(EntityType.PROCESS, process.getId(), configId,
process.getStatus(), TriggerType.USER_ACTION, context, process::setStatus)`. `@Transactional`
на самом методе (одна транзакция на загрузку + вызов движка + commit, как в Фазе 2).

Отсутствие конфига для `(entityType, processType, configVersion)` процесса →
`IllegalStateException` с понятным сообщением (не `NullPointerException`).

## T7. Открытый вопрос №2 — повторный `StartProcess` — Готово

Повторный вызов `startProcess` на уже `InProgress`-процессе → `NoApplicableTransitionException`
из `TransitionEngine` (Фаза 2): в конфиге V18 единственный `transition_config` заведён только
для `from_state = Draft`, для `InProgress` подходящих переходов нет вообще — это ровно случай
«явное отсутствие конфигурации», уже отличённый в Фазе 2 от «guards не прошли». Дополнительного
кода не потребовалось — поведение следует из уже принятого в Фазе 2 решения. Критерий приёмки 5
покрыт тестом, ожидающим именно это исключение.

## T8. Юнит-тесты guard-бинов (без БД) — Готово

По отдельности на каждый guard, сконструированные вручную `ProcessInstance`/`StageInstance`/
`StageIteration`/`Participant` через `@SuperBuilder` — без сохранения в БД, включая
намеренно некорректные данные (`duration <= 0`) для `AllDurationsValidGuard`, которые реальная
БД (CHECK-констрейнт) никогда не произведёт.

## T9. Интеграционный тест `ProcessService` (Testcontainers) — Готово

Реальные миграции `V1`–`V18` (без отдельной тестовой миграции — конфиг/реестры здесь не
тестовые заглушки, а часть сидируемых прод-данных). `ProcessInstance` со стадиями/итерациями/
участниками собирается в памяти и сохраняется одним `processRepository.save(process)`
(каскад). Критерии приёмки 1–5.

## T10. Сборка и прогон — Готово

`./gradlew build` (требует Docker для Testcontainers) — зелёный.
