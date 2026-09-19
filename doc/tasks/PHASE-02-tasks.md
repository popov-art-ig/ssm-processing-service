# PHASE-02 — разбивка на задачи

Источник: [`doc/tickets/PHASE-02-state-machine-engine.md`](../tickets/PHASE-02-state-machine-engine.md).
Реализация — одна ветка `feature/phase-2-state-machine-engine`, один PR в `main` по итогам
всех задач ниже (процесс из `TEMPLATE.md`: тикет = фаза, а не задача).

Статусы: `Не начато | В работе | Готово`.

## T1. Репозитории для конфигурации state machine — Готово

`StateMachineConfigRepository`, `StateConfigRepository`, `TransitionConfigRepository`
(`engine/model`) — простые запросы по `configId`, без `MultipleBagFetchException`
(раздельные запросы вместо двойного `join fetch` двух `List`-коллекций).

## T2. Guard/Action контракт и реестры — Готово

`Guard`/`Action` интерфейсы, `GuardRegistry`/`ActionRegistry`, `GuardRegistryRepository`/
`ActionRegistryRepository`, `ComponentResolver` (`engine/registry`, `engine`) — резолвинг
`handler` (имя бина, затем FQCN) через `ApplicationContext`, без прямого обращения к нему
из `TransitionEngine`.

## T3. ModelFactory — Готово

`TransitionKey`, `TransitionModel`, `ModelFactory` (`engine/model`) — строит in-memory модель
по явному `configId`; группировка переходов по `(fromState, trigger)`, сортировка по
`priority` (больший — первый), детерминированный tie-break по `code` (документировано в
Javadoc `TransitionModel`, т.к. в спеке порядок для равных `priority` не задан).

## T4. TransitionEngine — Готово

Реализует алгоритм из тикета дословно. Решения по открытым вопросам:

- **Отсутствие подходящего перехода** — `NoApplicableTransitionException`, если для
  `(fromState, trigger)` вообще нет активных `transition_config`. Если переходы есть, но
  у всех guards вернули `false` — не исключение, `TransitionResult.notPerformed(...)`
  (ожидаемый бизнес-результат — например, кнопка недоступна, а не баг вызывающего кода).
- **`TransitionContext`** — `record(Object entity, UUID actorId, ActorType actorType,
  Map<String,Object> parameters)`. Минимально достаточно для guard/action-заглушек этой
  фазы; ожидаемо расширится в фазах конкретных guards/actions.
- **Запись `status` в сущность без изменения `domain.process.*`** — `TransitionEngine`
  принимает `Consumer<String> statusWriter` (например, `process::setStatus`) вместо
  интерфейса `HasStatus`/рефлексии. Сущности Фазы 1 не модифицируются (тикет явно требует
  «без изменений»); `Consumer<String>` — минимальный функциональный контракт, разрешённый
  тикетом как альтернатива интерфейсу. Оптимистичная блокировка не реализуется вручную —
  используется штатный `@Version` сущности вызывающего кода: `TransitionEngine`
  только мутирует managed-сущность через `statusWriter`, flush/commit (и, соответственно,
  `OptimisticLockException` при конфликте) происходит на границе `@Transactional`, ничего
  не перехватывается и не глотается.

`StatusRegistryRepository`, `AuditEventRepository` (`engine`).

## T5. Юнит-тесты TransitionEngine (без БД) — Готово

Mockito-моки `GuardRegistry`/`ActionRegistry`/`StatusRegistryRepository`/
`AuditEventRepository`, `TransitionModel` собирается вручную в тесте. Критерии приёмки 1–4.

## T6. Тестовые guard/action-заглушки — Готово

`src/test/.../engine/testsupport`: `AlwaysTrueGuard`, `AlwaysFalseGuard`, `RecordingAction`
— помечены `@Profile("test")` и физически находятся в `src/test` (не попадают в
production jar по построению `bootJar`, а не только по профилю).

## T7. Интеграционный тест полного цикла (Testcontainers) — Готово

Сохранённый `StateMachineConfig`+`StateConfig`+`TransitionConfig` → `ModelFactory` →
`TransitionEngine.transition(...)` → `status` сущности обновлён в БД, создан `AuditEvent`.
Используются тестовые guard/action-заглушки из T6.

## T8. Тест оптимистичной блокировки — Готово

Критерий приёмки 5: конкурентное расхождение `version` → `OptimisticLockException`
(обёрнутое Spring в `ObjectOptimisticLockingFailureException`), не потерянное обновление.

## T9. Сборка и прогон — Готово

`./gradlew build` (включает `test`, требует Docker для Testcontainers) — зелёный.
