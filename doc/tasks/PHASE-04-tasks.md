# PHASE-04 — разбивка на задачи

Источник: [`doc/tickets/PHASE-04-stage-activation.md`](../tickets/PHASE-04-stage-activation.md).
Реализация — одна ветка `feature/phase-4-stage-activation`, один PR в `main`.

Статусы: `Не начато | В работе | Готово`.

## T1. Миграция V19 — Готово

`src/main/resources/db/migration/V19__seed_activate_stage_transition.sql`:
`guard_registry`/`action_registry`/`status_registry` для `STAGE`, `state_machine_config` +
`state_config` (`Pending`/`Active`) + `transition_config` (`ActivateStage`,
`entity_type='STAGE', process_type='STANDARD', version=1`) — по техническим требованиям
тикета (готового SQL, в отличие от V18, тикет не давал — собран по разделам 1 и 5).
Плюс `UPDATE transition_config SET actions = '{AssignStageTasks,SetStartedAt}' WHERE code =
'StartProcess'` (обновление конфига Фазы 3 in-place — обоснование см. тикет, раздел 3: на
момент этой фазы нет ни одного реального работающего процесса, Fork & Drain не нарушается).

## T2. `PreviousStageCompletedGuard` — Готово

`service.guard`, бин `previousStageCompletedGuard`. Для этапа без предыдущего (минимальный
`orderIdx`) — `true` (решение тикета по неуточнённому в спеке случаю). Иначе — статус
предыдущего этапа `∈ {Approved, ApprovedWithComments, Completed}`.

## T3. Actions уровня этапа — Готово

`AssignParticipantTasksAction`, `SetStageStartedAtAction`, `CalcStageDueAtAction`
(`service.action`, бины `assignParticipantTasksAction`/`setStageStartedAtAction`/
`calcStageDueAtAction`).

- `AssignParticipantTasksAction`: работает с участниками текущей (последней по
  `iterationIdx`) итерации; `Parallel`/`null` → все `Assigned`; `Sequential` → первый по
  `orderIdx` `Assigned`, остальные `Pending`. Нет итераций вообще →
  `IllegalStateException` (данные не могут быть активированы осмысленно); пустая текущая
  итерация без участников — не ошибка, просто no-op.
- `CalcStageDueAtAction`: единица `duration` — **дни** (открытый вопрос №2 тикета, решение
  по аналогии с `ScheduleAutoArchive`). Зависит от порядка: должен выполняться после
  `SetStageStartedAtAction` — порядок задан миграцией V19 (`actions` перехода
  `ActivateStage`), не кодом.

## T4. `AssignStageTasksAction` — Готово

`service.action`, бин `assignStageTasksAction`. `context.entity()` → `ProcessInstance`;
находит этап с минимальным `orderIdx`, резолвит `STAGE`-конфиг через уже существующий
`StateMachineConfigRepository.findByEntityTypeAndProcessTypeAndVersion` (Фаза 3, новый метод
не нужен), выполняет вложенный `transitionEngine.transition(EntityType.STAGE, ...)` в той же
транзакции, что и внешний `StartProcess`.

**Открытый вопрос №1 — решение.** Если вложенный переход не выполнен
(`TransitionResult.performed() == false`) — не молчать: `IllegalStateException`. В объёме
этой фазы это недостижимо на практике (первый этап всегда проходит
`PreviousStageCompletedGuard`), поэтому трактуется как нарушение инварианта, а не ожидаемый
бизнес-результат — тот же принцип «явная ошибка вместо тихого no-op», что уже принят в
открытых вопросах Фазы 2 (№1) и Фазы 3 (№2).

## T5. Юнит-тесты — Готово

`PreviousStageCompletedGuardTest` (нет предыдущего / `Approved` / `ApprovedWithComments` /
`Completed` / не завершён), `AssignParticipantTasksActionTest` (`Parallel`, `null` как
`Parallel`, `Sequential`, пустая итерация — no-op, нет итераций — исключение),
`CalcStageDueAtActionTest` (арифметика дней — единственный тест, который придётся поправить,
если единица окажется не днями).

## T6. Интеграционный тест — Готово

`ProcessServiceIntegrationTest` (Фаза 3) расширен, не переписан: реальные миграции теперь
`V1`–`V19`; фикстура `persistProcess` возвращает `ProcessFixture` (процесс + id первого этапа
+ id его участников), а не голый `ProcessInstance` — понадобилось, чтобы проверять
`stage.status`/`dueAt`/`participant.status` через отдельные `StageRepository`/
`ParticipantRepository` (новые, `domain.process`, минимальные), не упираясь в
`LazyInitializationException` при навигации по detached `ProcessInstance.stages`
(`open-in-view: false`). Начальный `stage.status` фикстуры изменён с плейсхолдера `"ACTIVE"`
(не имел значения до этой фазы) на `"Pending"` — обязательное условие, чтобы вложенный
`ActivateStage` вообще нашёл подходящий переход. Новый тест
`assignsOnlyFirstParticipantWhenExecutionOrderIsSequential` — критерий приёмки 3. Остальные
сценарии Фазы 3 (не-инициатор, незаполненные обязательные слоты, повторный `StartProcess`)
не переписаны по существу, только адаптированы под новую сигнатуру фикстуры.

## T7. Сборка и прогон — Готово

`./gradlew build` (требует Docker для Testcontainers) — зелёный.
