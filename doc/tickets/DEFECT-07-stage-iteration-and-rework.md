# DEFECT-07 — Статусы итераций, цикл возврата на доработку и отзыв в карте переходов

> Найден при документировании кода (PR #17). Исправление — ветка `fix/defect-07-iterations`.
> Связанные тикеты: DEFECT-02 (агрегация), DEFECT-03 (архивация), DEFECT-12 (цель возврата).

## Статус

`Не начато`

## Контекст

Итерация этапа задумана как круг согласования: при возврате на доработку этап активируется
заново уже новой итерацией, а предыдущая должна закрываться. В коде предыдущие итерации никто
не закрывает, а статусы, которые им присваиваются, не зарегистрированы в реестре статусов —
то есть итерация «живёт» без валидируемого состояния. Дополнительно в карте отсутствует
отзыв процесса, хотя статус `Recalled` в схеме объявлен.

## Источники (для истории, не для перехода)

- `04_state_machines.md` §6 — `StageIterationStateMachine`: состояния, переходы, логика создания
  итераций, вычисление «итерации процесса».
- `04_state_machines.md` §11.4 — действия возврата (`A-RT-003 CreateNewIterationFor`,
  `A-RT-004 ResetTargetStage`).
- `08_db_schema.md` §8.2 — таблица `stage_iteration` и её статусы.
- `05_guards_actions_registry.md` §5.2 `A-S-004 CloseIteration`.
- `doc/tickets/PHASE-08-process-resume.md`, `PHASES_09-28_BRIEF.md#phase-09` (отзыв).

## Что уже есть в репозитории

- `src/main/java/ru/coordination/approval/domain/process/StageIteration.java` — поля `status`,
  `startedAt`, `completedAt`, `iterationIdx`.
- `src/main/java/ru/coordination/approval/service/RouteGenerationService.java` — создаёт первую
  итерацию со статусом `"Pending"`.
- `src/main/java/ru/coordination/approval/service/action/ActivateTargetStageAction.java` — создаёт
  новую итерацию со статусом `"Active"` и клонирует участников.
- `src/main/java/ru/coordination/approval/service/guard/AllParticipantsDecidedGuard.java` — берёт
  последнюю итерацию по `max(iterationIdx)`.
- `src/main/resources/db/migration/V24__seed_remark_lifecycle_transitions.sql` — регистрирует
  статусы для `REMARK`; для `STAGE_ITERATION` строк в `status_registry` нет ни в одной миграции.

## Дефекты в объёме тикета

### 1. Статусы итерации не валидируются

`TransitionEngine.validateTargetStatus` проверяет `toState` по `status_registry` для
соответствующего `entityType`. Для `STAGE_ITERATION` ни одного статуса не зарегистрировано,
поэтому карта/действия могут писать произвольные строки, и никто этого не заметит. Фактически
в коде встречаются `"Pending"` (создание) и `"Active"` (возврат на этап) — набор не совпадает
даже сам с собой.

### 2. Предыдущая итерация не закрывается

При возврате на доработку `ActivateTargetStageAction` создаёт новую итерацию, но у предыдущей
не проставляются ни `completedAt`, ни терминальный статус. По `04_state_machines.md` §6 переход
`CloseIteration` (`A-S-004`) должен закрывать итерацию. Следствия: в UI одновременно «активны»
несколько итераций; `AllParticipantsDecidedGuard` всегда смотрит на последнюю и поэтому не
замечает незакрытые решения предыдущих; аналитика по числу кругов согласования невозможна.

### 3. Нет отзыва процесса в карте переходов

`status_registry` содержит `Recalled` как терминальный статус процесса (V23), в коде есть
`ProcessService.recall` и guard `IsRecallRequest`, но перехода `RecallProcess` в
`transition_config` **нет**: карта из V23 регистрирует только `ProcessRework`, `ResumeProcess`,
`ReactivateStage`. Вызов `recall` приведёт к выбору другого `USER_ACTION`-перехода из текущего
статуса (`StartProcess` для `Draft`, `ProcessRework` для `InProgress`) — см. DEFECT-05.
Требуется сверить: отзыв должен входить в карту (PHASE-09) или `ProcessService.recall` временно
не поддерживается и должен явно отказывать.

## Явно не входит (Out of scope)

- PHASE-09 целиком (`A-P-004 CancelAllTasks`, `TakeBackInWork`, `RevokeActiveTasks`).
- Ревизии документа (`ProcessInstance.revisionLabel`, PHASE-26).
- UI-отображение истории итераций.

## Технические требования

1. Зарегистрировать в `status_registry` полный набор статусов `STAGE_ITERATION` из
   `04_state_machines.md` §6.1 (миграцией V26+), с корректным `is_terminal`. Использовать в
   коде только эти коды.
2. Закрывать предыдущую итерацию при активации новой: `completedAt = now()`, терминальный
   статус (`Completed`/`Superseded` — по §6.1). Реализовать как action `CloseIteration`
   (`A-S-004`) и включить его в переходы возврата, а не писать статус напрямую из
   `ActivateTargetStageAction`.
3. Привести статус первой итерации к реестру: сейчас `RouteGenerationService` пишет `"Pending"`.
   Если по §6.1 начальный статус иной — исправить; если `Pending` корректен — зарегистрировать
   его.
4. Решить судьбу отзыва: либо добавить переход `RecallProcess` в карту (и тогда DEFECT-05
   ограничивается предусловием и аудитом), либо явно задокументировать, что отзыв недоступен,
   и убрать недостижимый код (`IsRecallRequestGuard`, ветка в `ProcessService.recall`).
5. Правки применённых миграций не допускаются: новые статусы — новой миграцией.

## Критерии приёмки

1. Попытка записать статус итерации, отсутствующий в `status_registry`, приводит к
   `InvalidTargetStatusException` (проверяется тестом движка на `EntityType.STAGE_ITERATION`).
2. Интеграционный тест: после возврата на доработку у предыдущей итерации заполнены `completedAt`
   и терминальный статус; активна ровно одна итерация этапа.
3. Интеграционный тест: три круга «возврат → возобновление» дают три итерации с
   `iterationIdx` 0,1,2, каждая предыдущая закрыта.
4. Тест на отзыв: либо `recall` переводит процесс в `Recalled` (переход есть в карте), либо
   возвращает осмысленный отказ — в зависимости от решения по п. 4; тест фиксирует выбранное
   поведение.
5. `./gradlew build` зелёный; `ProcessResumeIntegrationTest`, `TransitionEngineIntegrationTest`
   обновлены осознанно.

## Тестирование

- Юнит: тест действия закрытия итерации; тест на выбор последней итерации guard'ом агрегации.
- Интеграционные (Testcontainers): `ProcessResumeIntegrationTest` — сценарии из критериев 2–3;
  новый тест на `STAGE_ITERATION`-статусы.
- Регрессия: `StageAggregationIntegrationTest`, `ProcessCompletionIntegrationTest`.

## Открытые вопросы

1. **Какие статусы итерации каноничны?** `04_state_machines.md` §6.1 описывает набор, но
   `08_db_schema.md` §8.2 в комментариях упоминает `Active/Completed/Superseded/Cancelled`.
   Расхождение нужно закрыть до реализации — иначе миграция закрепит неверный набор.
2. **Отзыв процесса:** входит в объём следующей фазы (PHASE-09) или отзыв нужен сейчас?
   От ответа зависит, правится карта в этом тикете или в отдельном.
3. Нужно ли переносить в новую итерацию решения/замечания предыдущей, или они всегда начинаются
   «с чистого листа»? Сейчас клонируются только участники (в статусе `Pending`).
