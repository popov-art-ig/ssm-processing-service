# DEFECT-02 — Агрегация решений этапа: расхождения с `evaluateAggregation` по четырём режимам

> Найден при документировании кода (PR #17). Исправление — ветка `fix/defect-02-aggregation`.
> Связанные тикеты: DEFECT-01 (замечания), DEFECT-07 (итерации и возврат).

## Статус

`Не начато`

## Контекст

Итог этапа определяют три guard'а (`G-S-002`–`G-S-004`), которые поверх одной функции
`evaluateAggregation(stage)` выбирают исход `APPROVED` / `APPROVED_WITH_COMMENTS` / `REWORK` /
`WAIT`. В коде каждый guard разбирает `decisionMode` собственным `switch`, и часть режимов
ведёт себя иначе, чем спецификация. В худшем случае комбинация «есть согласование + есть
отклонение» не удовлетворяет ни одному guard'у, и этап **навсегда остаётся `Active`** —
процесс дальше не двигается.

## Источники (для истории, не для перехода)

- `05_guards_actions_registry.md` §4.2 и «Модель агрегации решений (общая для G-S-002 – G-S-004)»
  (строка ~276) — текст функции `evaluateAggregation` и таблица режимов.
- `04_state_machines.md` §5.2 — переходы `StageApproved`/`StageApprovedWithComments`/`StageOnRework`.
- `11_adr.md` ADR-022 — параметризуемая агрегация решений.
- `doc/tickets/PHASE-06-stage-aggregation.md` — постановка агрегации.

## Что уже есть в репозитории

- `src/main/java/ru/coordination/approval/domain/common/DecisionMode.java` — пять режимов
  (`AND`, `ANY_APPROVE`, `ANY_REJECT`, `ANY_DECISION`, `FIRST_REJECT_FAIL_FAST`) с описанием
  ожидаемой семантики.
- `src/main/java/ru/coordination/approval/service/guard/AggregationApprovedGuard.java`,
  `AggregationApprovedWithCommentsGuard.java`, `AggregationReworkGuard.java`.
- `src/main/java/ru/coordination/approval/service/action/EvaluateAggregationAction.java` —
  запускает агрегацию как action перехода `Decide`.
- `src/main/java/ru/coordination/approval/domain/process/DecisionRepository.java` — источник
  истины о голосовании (`decision`, не `participant.status`).
- Тесты: `AggregationApprovedGuardTest`, `AggregationApprovedWithCommentsGuardTest`,
  `AggregationReworkGuardTest`, `StageAggregationIntegrationTest`.

## Дефекты в объёме тикета

### 1. `ANY_APPROVE` в `AggregationReworkGuard` — инвертированное условие

```java
case ANY_APPROVE -> decisions.stream().noneMatch(d -> "REJECT".equals(d.getResult()));  // ожидается anyMatch
```

В режиме `ANY_APPROVE` этап уходит на доработку **только если отклонения нет**, то есть
«доработка» срабатывает на согласовании, а на отклонении — нет. По спецификации §4.2 `REWORK`
в этом режиме возникает при `anyMatch(REJECT)`.

### 2. `ANY_APPROVE` в `AggregationApprovedGuard` игнорирует `APPROVE_WITH_COMMENTS`

```java
case ANY_APPROVE -> decisions.stream().anyMatch(d -> "APPROVE".equals(d.getResult()));
```

Спецификация считает положительным и `APPROVE`, и `APPROVE_WITH_COMMENTS` (с разным исходом:
`APPROVED` против `APPROVED_WITH_COMMENTS`).

### 3. Комбинация дефектов 1–2 останавливает этап навсегда

Этап из двух участников, один согласовал и один отклонил, в режиме `ANY_APPROVE`:
`AggregationApproved` → `false`, `AggregationApprovedWithComments` → `false`,
`AggregationRework` → `false` (условие инвертировано). Ни один переход не выполняется,
`StageInstance.status` остаётся `Active`, процесс не завершается и не уходит на доработку.
Воспроизводится интеграционно.

### 4. `ANY_DECISION` закрывает этап как `APPROVED` при первом `REJECT`

```java
case ANY_DECISION -> true;
```

Спецификация: самое раннее по `decidedAt` решение определяет исход (`REJECT` → `REWORK`,
`APPROVE_WITH_COMMENTS` → `APPROVED_WITH_COMMENTS`, `APPROVE` → `APPROVED`). В коде порядок
решений не рассматривается, и первый же голос «против» даёт согласование этапа.

### 5. Дублирование логики вместо общей функции

Спецификация описывает одну `evaluateAggregation(stage)` и три тонких предиката над её
результатом. В коде режимная логика продублирована в трёх guard'ах, а
`AggregationApprovedWithCommentsGuard` содержит ещё и приватную копию проверки. Любое
исправление приходится делать в нескольких местах — именно поэтому дефекты 1–4 сосуществуют.

## Явно не входит (Out of scope)

- Изменение набора режимов `DecisionMode` и их названий.
- Модалка выбора режима в UI и валидация `decision_mode` в шаблонах
  (`ValidateTemplateStructureAction` уже проверяет обязательность поля для STANDARD).
- Автосогласование по срокам (`A-S-005 AutoApproveMissing`, PHASE-16).

## Технические требования

1. Ввести один компонент (например `AggregationEvaluator` в `service.guard` или
   `engine`-нейтральном пакете), возвращающий исход `APPROVED` / `APPROVED_WITH_COMMENTS` /
   `REWORK` / `WAIT` по `StageIteration` + `DecisionMode`. Три guard'а становятся предикатами
   над этим результатом; дублирование `switch` устраняется.
2. Реализовать режимы буквально по §4.2:

   | Режим | Условие закрытия | Исход |
   |---|---|---|
   | `AND` | все участники итерации решили | `REWORK` при любом `REJECT`, иначе `APPROVED_WITH_COMMENTS` при наличии такого решения, иначе `APPROVED` |
   | `ANY_APPROVE` | есть `APPROVE` или `APPROVE_WITH_COMMENTS` | `APPROVED_WITH_COMMENTS`, если первый положительный по `decidedAt` — с замечаниями, иначе `APPROVED` |
   | `ANY_REJECT` | есть `REJECT` | `REWORK`; иначе `WAIT` |
   | `ANY_DECISION` | есть хотя бы одно решение | по самому раннему `decidedAt`: `REJECT` → `REWORK`, `APPROVE_WITH_COMMENTS` → `APPROVED_WITH_COMMENTS`, `APPROVE` → `APPROVED` |
   | `FIRST_REJECT_FAIL_FAST` | есть `REJECT` или решили все | `REWORK` при `REJECT`, иначе как `AND` без `REJECT` |

3. Порядок решений брать по `decision.recorded_at` (при равенстве — по `participant.order_idx`
   для детерминизма).
4. `WAIT` не должен приводить к выполнению ни одного из переходов этапа — проверить, что
   `TransitionEngine` возвращает `notPerformed`, а не бросает исключение.
5. Сохранить совместимость с `EvaluateAggregationAction`: она вызывается как action перехода
   `Decide` **после** записи решения, но **до** смены статуса участника движком — источник
   истины остаётся `decision`, а не `participant.status` (см. Javadoc
   `AllParticipantsDecidedGuard` и `DecisionRepository`).

## Критерии приёмки

1. Таблица режимов из п. 2 реализована и покрыта параметризованным юнит-тестом на все пять
   режимов × наборы решений (`все approve`, `approve+reject`, `approve_with_comments`,
   `первый reject при ANY_DECISION`, `частичное голосование`).
2. Для сценария «один согласовал, один отклонил» в `ANY_APPROVE`: регрессионный интеграционный
   тест подтверждает, что этап **не** остаётся `Active` (переходит в `Approved` или `OnRework`
   по правилу режима).
3. В `ANY_DECISION` при первом `REJECT` этап уходит в `OnRework`, а не в `Approved`.
4. В коде не остаётся второго `switch (decisionMode)` — поиск по `decisionMode` даёт один
   компонент-вычислитель и три предиката.
5. `./gradlew build` зелёный, все существующие тесты агрегации обновлены осознанно (изменение
   ожиданий обосновано в описании PR со ссылкой на §4.2).

## Тестирование

- Юнит: новый `AggregationEvaluatorTest` (параметризованный), плюс обновлённые
  `AggregationApprovedGuardTest`, `AggregationApprovedWithCommentsGuardTest`,
  `AggregationReworkGuardTest`.
- Интеграционные: `StageAggregationIntegrationTest` — добавить сценарии конфликта голосов для
  каждого режима; проверить, что после закрытия этапа активируется следующий (`ActivateNextStage`).
- Регрессия: `TransitionEngineIntegrationTest`, `ProcessCompletionIntegrationTest`.

## Открытые вопросы

1. `FIRST_REJECT_FAIL_FAST` и `AND` различаются только моментом выхода: подтвердите, что при
   отсутствии отклонений оба дают одинаковый исход (в текущем коде они совпадают — это
   соответствует §4.2 или нет?).
2. Что делать с решением `APPROVE_WITH_COMMENTS` при `AND`, если `REJECT` пришёл позже: исход
   `REWORK` (по §4.2 «любой REJECT») или `APPROVED_WITH_COMMENTS`? Тикет исходит из `REWORK`.
3. Нужно ли фиксировать исход агрегации в `audit_event.payload` (сейчас там только
   `fromState`/`toState`/`trigger`/`emits`)?
