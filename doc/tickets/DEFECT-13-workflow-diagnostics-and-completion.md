# DEFECT-13 — Диагностика незавершённых и «несработавших» переходов, автоархивация

> Найден при документировании кода (PR #17). Исправление — ветка `fix/defect-13-diagnostics`.
> Связанные тикеты: DEFECT-05 (аудит отказа), DEFECT-07 (итерации), PHASE-18 (автоархивация).

## Статус

`Не начато`

## Контекст

Несколько actions выполняют вложенный переход и молча игнорируют результат «переход не
выполнен» (`performed == false`), не оставляя ни лога, ни признака в аудите. Отличить штатную
ситуацию («ещё рано, guards не пропустили») от сломанной конфигурации по логам невозможно, а
процесс в это время не двигается. Плюс при завершении процесса не заполняется дата
автоархивации, хотя действие для этого предусмотрено спецификацией.

## Источники (для истории, не для перехода)

- `05_guards_actions_registry.md` §5.1 `A-P-003 SetCompletedAt`, `A-P-007 ScheduleAutoArchive`;
  §5.2 `A-S-006 ActivateNextStage`; §5.3 — действия возврата.
- `04_state_machines.md` §13.1 — автоматическая архивация процессов.
- `11_adr.md` ADR-005 (только автоматическая архивация), ADR-027 (настройки напоминаний — пример
  конфигурации из БД).
- `doc/tickets/PHASES_09-28_BRIEF.md#phase-18` — автоархивация.

## Что уже есть в репозитории

- `src/main/java/ru/coordination/approval/service/action/EvaluateProcessCompletionAction.java` —
  вложенный переход процесса; при `performed == false` ничего не делает.
- `src/main/java/ru/coordination/approval/service/action/EvaluateProcessReworkAction.java` — то же
  для возврата на доработку.
- `src/main/java/ru/coordination/approval/service/action/ActivateTargetStageAction.java` — бросает
  `IllegalArgumentException("Target stage not found")` и `IllegalStateException("No iterations
  found")` без идентификаторов процесса/этапа.
- `src/main/java/ru/coordination/approval/service/action/CompleteProcessAction.java` — не
  заполняет `autoArchiveScheduledAt`.
- `src/main/java/ru/coordination/approval/domain/process/ProcessInstance.java` — поля
  `autoArchiveScheduledAt`, `archivedAt`.
- `src/main/java/ru/coordination/approval/engine/TransitionEngine.java` — логирует выбор перехода
  и отказ guard'а на уровне `debug`.

## Дефекты в объёме тикета

### 1. `performed == false` не логируется

В `EvaluateProcessReworkAction` и `EvaluateProcessCompletionAction` ветка отказа оставлена
пустой (с пояснительным комментарием, без `log`). Диагностика возможна только при
`logging.level.ru.coordination.approval=debug`, и то — по строке движка, без указания, какое
именно действие не сработало.

### 2. Неинформативные сообщения об ошибках

`ActivateTargetStageAction` бросает исключения без `processId`/`orderIdx`: по логу нельзя найти
данные, на которых воспроизводится проблема. То же в `EvaluateProcessCompletionAction`
(«No StateMachineConfig for entityType=PROCESS...» без идентификатора процесса — там есть только
тип и версия).

### 3. Автоархивация не планируется

`CompleteProcessAction` (`A-P-003 SetCompletedAt`) выставляет `completedAt`, но не
`autoArchiveScheduledAt`, а действия `A-P-007 ScheduleAutoArchive` в карте нет. Следствие:
`08_db_schema.md` §13 и PHASE-18 не реализуемы, признака «когда архивировать» в данных нет.
Задача может быть частично закрыта в этом тикете (поле + простая формула) либо вынесена в
PHASE-18 — решение зафиксировать.

### 4. Отказ перехода не оставляет следа в аудите

Общий случай описан в DEFECT-05 (аудит `notPerformed`). Здесь важно следствие именно для
завершения процесса: «все этапы согласованы, но процесс не закрылся» нигде не фиксируется.

## Явно не входит (Out of scope)

- Сам джоб автоархивации и планировщик (PHASE-18, DEFECT-18).
- Восстановление процесса из архива (`A-P-009`, `POST /admin/processes/{id}/restore` — заглушка).
- Настройки периодичности напоминаний (ADR-027).

## Технические требования

1. Логировать `performed == false` на уровне `WARN` с контекстом: имя действия, `entityType`,
  `entityId` (`processId`/`stageId`), текущий статус, причина (если движок может отдать код
   непройденного guard'а — использовать его; см. DEFECT-05, п. 3). Штатные случаи («ещё не все
   этапы завершены») допустимо логировать на `INFO`/`DEBUG`, но отличать их от конфигурационных
   ошибок.
2. Добавить идентификаторы в тексты исключений `ActivateTargetStageAction` и
   `EvaluateProcessCompletionAction` (процесс, этап, `orderIdx`, версия карты).
3. Реализовать планирование автоархивации: при завершении процесса заполнять
   `autoArchiveScheduledAt = completedAt + N дней` (N — по спецификации; если значение
   настраивается — читать из `notification_settings`-подобного источника, а не хардкодить).
   Действие оформить как `A-P-007 ScheduleAutoArchive` и включить в карту переходов завершения,
   а не писать поле из `CompleteProcessAction` напрямую.
4. Если решение — отложить автоархивацию до PHASE-18, явно задокументировать это в Javadoc
   `ProcessInstance.autoArchiveScheduledAt` и оставить в критериях тикета пометку «перенесено».

## Критерии приёмки

1. Тест: при `performed == false` во вложенном переходе завершения процесса в логе есть запись
   уровня `WARN` с `processId` и причиной (проверяется через перехват логов в тесте).
2. Тест: `ActivateTargetStageAction` при отсутствии целевого этапа бросает исключение, текст
   которого содержит `processId` и `orderIdx`.
3. Если п. 3 реализуется: после завершения процесса `autoArchiveScheduledAt` заполнен и равен
   `completedAt + N`; действие `ScheduleAutoArchive` зарегистрировано в реестре и включено в
   карту, `TransitionMapValidator` при старте не находит нарушений.
4. Если п. 3 переносится: в Javadoc поля и в тикете PHASE-18 зафиксировано, что поле пока не
   заполняется, и почему.
5. `./gradlew build` зелёный; `ProcessCompletionIntegrationTest`, `EvaluateProcessCompletionActionTest`,
   `EvaluateProcessReworkActionTest` обновлены осознанно.

## Тестирование

- Юнит: `EvaluateProcessCompletionActionTest`, `EvaluateProcessReworkActionTest` — кейс
  `performed == false` с проверкой лога; `ActivateTargetStageActionTest` — текст исключения.
- Интеграционные: `ProcessCompletionIntegrationTest` — заполнение `autoArchiveScheduledAt`
  (если реализуется).
- Регрессия: `TransitionEngineIntegrationTest` (аудит и логи).

## Открытые вопросы

1. **Период автоархивации:** фиксированные 180 дней, конфигурация в БД или параметр
   `application.yml`? В репозитории сейчас нет ни одного источника.
2. **Кто планирует архивацию:** действие карты (`A-P-007`) или сервис после завершения процесса?
3. Нужно ли логировать отказ перехода на `WARN` для **всех** вложенных переходов или только для
   завершения процесса/возврата (чтобы не зашуметь логи на штатных сценариях `WAIT`)?
