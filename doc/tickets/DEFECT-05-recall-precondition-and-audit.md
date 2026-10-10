# DEFECT-05 — Отзыв процесса без предусловия статуса и без аудита отклонённого перехода

> Найден при документировании кода (PR #17). Исправление — ветка `fix/defect-05-recall`.
> Связанные тикеты: DEFECT-03 (архивация шаблона), DEFECT-13 (диагностика).

## Статус

`Не начато`

## Контекст

У методов жизненного цикла процесса есть явные предусловия по статусу
(`startProcess` требует `Draft`, `resume` — `OnRework`), а у `recall` — нет. Из состояния
`Draft` по `USER_ACTION` объявлен переход `StartProcess`, поэтому вызов «отозвать» на черновике
выполнит **запуск** процесса: `Draft → InProgress` с активацией первого этапа. Параллельно в
движке не фиксируется факт отказа: если guards перехода не прошли, вызывающий получает
`notPerformed`, но в `audit_event` этого не видно, а `ProcessController.recallProcess` всё равно
отвечает `200` с прежним статусом.

## Источники (для истории, не для перехода)

- `04_state_machines.md` §4.2, §4.4 — переходы `ProcessStateMachine` для STANDARD, включая
  `RecallProcess`.
- `05_guards_actions_registry.md` §4.1 `G-P-001 IsInitiator`; действие `A-P-004 CancelAllTasks`.
- `07_api_contract.md` §12 — контракт процессов (отзыв, коды ответов).
- `doc/tickets/PHASE-08-process-resume.md`, `doc/tickets/PHASES_09-28_BRIEF.md#phase-09` (отзыв).

## Что уже есть в репозитории

- `src/main/java/ru/coordination/approval/service/ProcessService.java` — `startProcess`,
  `recall`, `resume`.
- `src/main/java/ru/coordination/approval/service/guard/IsRecallRequestGuard.java` — отличает
  отзыв от возобновления по наличию параметра `reason`.
- `src/main/java/ru/coordination/approval/engine/TransitionEngine.java` — алгоритм перехода,
  запись `audit_event` только для выполненного перехода.
- `src/main/java/ru/coordination/approval/api/controller/ProcessController.java` — `recallProcess`.
- `src/main/resources/db/migration/V18__seed_process_state_machine.sql` (переход `StartProcess`),
  `V23__seed_process_rework_resume_transitions.sql` (переходы `RecallProcess`, `ResumeProcess`).

## Дефекты в объёме тикета

### 1. `recall` может запустить черновик

```java
public TransitionResult recall(UUID processId, UUID actorId, String reason) {
    ProcessInstance process = requireProcess(processId);
    Map<String, Object> parameters = Map.of("reason", reason == null ? "" : reason);
    return transitionEngine.transition(..., TriggerType.USER_ACTION, ..., process::setStatus);
}
```

Предусловия по статусу нет. Для `Draft` движок находит кандидата `StartProcess`
(`Draft → InProgress`) — guards `IsInitiator`, `AllMandatorySlotsFilled`, `AllDurationsValid`
проходят или не проходят, но отзыв заведомо не то, что ожидает вызывающий. Тот же риск для
статусов, где по `USER_ACTION` есть другой переход (`InProgress` → `ProcessRework` при
непустых замечаниях).

### 2. Отказ guards не попадает в аудит

`TransitionEngine.transition` при непройденных guards возвращает `TransitionResult.notPerformed`,
не записывая `AuditEvent`. Для отзыва это означает: «пользователь нажал отозвать, ничего не
произошло» — и в журнале ни следа. Отдельно: `EvaluateProcessReworkAction` и
`EvaluateProcessCompletionAction` глушат `performed == false` без лога (см. DEFECT-13).

### 3. Контроллер возвращает 200 при несостоявшемся отзыве

`ProcessController.recallProcess` не проверяет `TransitionResult.performed()`: ответ `200` с
прежним статусом процесса. Клиент не может отличить успех от отказа guard'а.

## Явно не входит (Out of scope)

- Реализация `A-P-004 CancelAllTasks` (отмена задач участников) — это PHASE-09 целиком.
- Возобновление после отзыва (`TakeBackInWork`) — PHASE-09.
- Изменение карты переходов: тикет не добавляет и не удаляет переходы, только проверяет
  предусловия в сервисе.

## Технические требования

1. В `ProcessService.recall` добавить явное предусловие по допустимым статусам
   (`InProgress`, `OnRework`) с `IllegalStateException`, как в `startProcess`/`resume`.
   Допустимый набор читать не из константы, а из карты переходов: выбрать коды статусов, из
   которых существует переход с тем же `fromState` и триггером `USER_ACTION`, ведущий в
   `Recalled` (запрос к `TransitionConfigRepository` по `configId` процесса). Это устраняет
   хардкод и автоматически учитывает изменения карты.
2. Если переход не найден или guards не прошли — не возвращать «тихий» `200`: сервис должен
   вернуть результат с `performed == false`, а контроллер — `409 CONFLICT` с пояснением
   (например, «процесс в статусе X не может быть отозван»).
3. Записывать факт отказа в аудит: добавить `AuditEvent` с результатом `notPerformed`,
   указанием `fromState`, триггера и кода первого непройденного guard'а (движок уже вычисляет
   его в `firstFailingGuard` — достаточно пробросить наружу). Это касается всех переходов, а
   не только отзыва.

## Критерии приёмки

1. Интеграционный тест: `recall` на процессе в `Draft` не меняет статус и не выполняет
   `StartProcess`; ответ REST — `409`, а не `200`.
2. Тест: `recall` в `InProgress` переводит процесс в `Recalled` (при прохождении `IsInitiator`).
3. Тест: `recall` в `Approved` (терминальный статус) отклоняется с `409`, статус не меняется.
4. Тест: при непройденном guard'е в `audit_event` появляется запись об отказе с кодом guard'а;
   для выполненного перехода формат `payload` не меняется.
5. `./gradlew build` зелёный; `ProcessApiIntegrationTest`, `ProcessResumeIntegrationTest` не сломаны.

## Тестирование

- Интеграционные (Testcontainers): расширить `ProcessServiceIntegrationTest` и
  `ProcessApiIntegrationTest` сценариями из критериев 1–4.
- Юнит: тест на выбор допустимых статусов из карты (фиктивный `TransitionConfigRepository`).
- Регрессия: `TransitionEngineIntegrationTest` — проверить, что запись аудита об отказе не
  ломает существующие ожидания по количеству событий.

## Открытые вопросы

1. Нужен ли отдельный тип `AuditEvent` для отказа или достаточно поля-маркера в `payload`
   (`result: notPerformed`)? Тикет предлагает поле в `payload`, чтобы не менять схему.
2. Должен ли `recall` быть идемпотентным (повторный отзыв отозванного процесса → `200`)?
3. Нужны ли права на отзыв помимо `IsInitiator` (администратор, ответственный)?
