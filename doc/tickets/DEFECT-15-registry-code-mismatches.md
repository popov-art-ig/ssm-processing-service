# DEFECT-15 — Коды `G-*`/`A-*` в реестрах V19–V25 не совпадают со спецификацией

> Найден при документировании кода (PR #17). Исправление — ветка `fix/defect-15-registry-codes`.
> Связанные тикеты: DEFECT-16 (чистка миграций), DEFECT-20 (документация).

## Статус

`Не начато`

## Контекст

Каждая запись `guard_registry`/`action_registry` ссылается на код условия/действия из
спецификации (`05_guards_actions_registry.md` §4–5, сводная таблица §10). Эти коды — язык, на
котором карта переходов, реестр и спецификация разговаривают между собой: по ним админ ищет
условие в спецификации, а валидация и UI — связывают запись реестра с документацией. В
миграциях V19–V25 часть кодов проставлена неверно (сдвиг нумерации, ссылки на занятые коды,
ссылки на несуществующие `G-R-*`). Расхождение опасно тем, что выглядит достоверно: админ
открывает спецификацию по указанному коду и видит **другое** условие.

## Источники (для истории, не для перехода)

- `05_guards_actions_registry.md` §4.1–4.8 (определения условий), §5.1–5.9 (действия),
  §10.1–10.2 (сводные таблицы — источник истины по кодам).
- `04_state_machines.md` §11.3 (условия возврата), §5.2 (условия этапа).

## Что уже есть в репозитории

Реестры заполняются сид-миграциями:
`V18__seed_process_state_machine.sql`, `V19__seed_activate_stage_transition.sql`,
`V20__seed_decide_transition.sql`, `V21__seed_stage_aggregation_transitions.sql`,
`V22__seed_process_completion_transitions.sql`,
`V23__seed_process_rework_resume_transitions.sql`,
`V24__seed_remark_lifecycle_transitions.sql`, `V25__seed_template_state_machine.sql`.
Тексты описаний лежат в колонке `description` соответствующих таблиц; см. также Javadoc классов
`ru.coordination.approval.service.guard.*` и `service.action.*`.

## Таблица расхождений (проверено по миграциям и §10)

| Миграция | Запись | Код в миграции | Код по спецификации |
|---|---|---|---|
| V19 | `AssignParticipantTasks` | `A-S-002... A-S-001` (самопротиворечиво) | `A-S-001 AssignParticipantTasks`; `A-S-002` — это `SetStageStartedAt` |
| V20 | `IsAssignedParticipant` | `G-PA-001` | совпадает (`G-PA-001 IsParticipant`), но запись названа иначе |
| V20 | `RecordComment` | `A-PA-002` | кода нет вовсе: комментарий — параметр `A-PA-001 RecordDecision` |
| V20 | `EvaluateAggregation` | `A-S-004` | `A-S-004` — это `CloseIteration`; для агрегации кода в спецификации нет |
| V21 | `AllParticipantsDecided` | `G-S-002` | `G-S-002` — это `AggregationApproved`; отдельного кода для «все решили» нет (это часть `evaluateAggregation`) |
| V21 | `AggregationApproved` | `G-S-003` | `G-S-002 AggregationApproved` |
| V21 | `AggregationApprovedWithComments` | `G-S-004` | `G-S-003 AggregationApprovedWithComments` |
| V21 | `AggregationRework` | `G-S-005` | `G-S-004 AggregationRework` (`G-S-005` — `DueDateExpired`) |
| V22 | `AllStagesCompleted` | `G-P-006` | `G-P-007 AllStagesApproved` (`G-P-006` — `AllActiveRemarksHandled`) |
| V22 | `HasComments` | `G-P-007` | `G-P-008 AllStagesApprovedWithComments` |
| V22 | `CompleteProcess` | `A-P-002` | `A-P-003 SetCompletedAt` (`A-P-002` — `SetStartedAt`) |
| V22 | `EvaluateProcessCompletion` | `A-S-005` | `A-S-005` — это `AutoApproveMissing`; для завершения процесса кода нет |
| V23 | `HasStageOnRework` | `G-P-007` | `G-P-007` — `AllStagesApproved`; отдельного кода для «есть этап на доработке» нет |
| V23 | `AllRemarksProcessed` | `G-P-008` | `G-P-006 AllActiveRemarksHandled` (см. DEFECT-01) |
| V23 | `IsTargetStageAllowed` | `G-P-009` | `G-RT-001 IsValidReturnTarget` (`G-P-009` — `AnyStageRejected`) |
| V24 | `IsParticipant` | `G-R-001` | семейства `G-R-*` в спецификации нет; ближайшее — `G-PA-001 IsParticipant` |
| V24 | `IsRemarkAuthor` | `G-R-002` | нет такого кода (в спецификации §4.6 другие условия) |
| V24 | `IsRemarkAssignee` | `G-R-003` | нет такого кода |
| V24 | `HasUnprocessedRemarks` | `G-P-010` | `G-P-010` не существует (последний в §4.1 — `G-P-009`) |
| V24 | `AssignRemarkToAuthor` | `A-R-001` | `A-R-*` в спецификации нет; назначение описано как часть `A-PA-001` |
| V24 | `NotifyRemarkStatusChange` | `A-R-002` | нет такого кода (уведомления — `A-P-005 NotifyParticipants`, PHASE-19) |
| V25 | `AllMandatorySlotsValid`, `NoActiveProcesses`, `SetPublishedTimestamp`, `SetArchivedTimestamp`, `ValidateTemplateStructure` | кодов нет | жизненный цикл шаблона в §4–5 не описан (замечание зафиксировано в Javadoc) |

## Явно не входит (Out of scope)

- Дубликат `ReactivateStage` в V23 и самопротиворечивый комментарий в V20 — см. DEFECT-16.
- Исправление самих наборов статусов замечаний — см. DEFECT-01.
- Реализация `G-RT-002 HasMultipleReturnTargets` — см. DEFECT-12 (здесь только код в реестре).

## Технические требования

1. **Правка применённых миграций запрещена**: Flyway сверяет контрольные суммы, и изменение
   V19–V25 сломает уже развёрнутые БД. Все исправления — новой миграцией `V26__fix_registry_codes.sql`
   (или следующим свободным номером), обновляющей колонку `description` записей реестра через
   `update ... where code = ...`.
2. Для каждой записи выбрать одно из двух:
   - указать **корректный** код из §10, если условие/действие там есть;
   - если кода в спецификации нет (что верно для большей части таблицы) — писать
     `код в спецификации отсутствует` с коротким пояснением, чем эта запись отличается от
     ближайшего описанного условия. Никаких «похожих» кодов вместо отсутствующих.
3. Для трёх случаев, где условие в спецификации есть, а реализация — упрощение
   (`IsTargetStageAllowed` → `G-RT-001`, `AllRemarksProcessed` → `G-P-006`,
   `AllStagesCompleted` → `G-P-007`), после исправления описания привести реализацию в
   соответствие (это уже покрыто DEFECT-03/12 и DEFECT-01 — здесь только описания).
4. Обновить `doc/JAVADOC_COVERAGE.md` §4 (пункт про 18 некорректных ссылок) и, если решено,
   `README.md` (раздел валидации карт не затрагивается).
5. Описания в реестре должны быть согласованы с Javadoc классов guard/action: одна и та же
   формулировка про упрощение/заглушку (Javadoc правился в PR #17 — перенести формулировки).

## Критерии приёмки

1. Новая миграция применяется на чистой БД и на БД, где V1–V25 уже применены (проверяется
   интеграционным тестом с Testcontainers).
2. Скрипт/тест сравнивает коды из описаний реестра со сводной таблицей §10: для записей,
   у которых код должен существовать, расхождений нет; для остальных явно написано
   «кода в спецификации нет».
3. В описаниях реестра не остаётся ссылок на несуществующие семейства (`G-R-*`, `A-R-*`) и на
   занятые коды (`G-P-010`, `G-S-005` для `AggregationRework` и т.п.).
4. При старте приложения `TransitionMapValidator` не находит нарушений (описания на валидацию не
   влияют — проверка на регресс).
5. `./gradlew build` зелёный.

## Тестирование

- Интеграционный: применение новой миграции поверх существующей схемы (Testcontainers, профиль
  `test` с `db/migration` + `db/test-migration`).
- Юнит/интеграционный: тест-«линтер» по реестру — проходит по всем записям и проверяет
  формат/существование упомянутых кодов (можно оформить как `TransitionMapValidator`-подобный
  стартовый валидатор).
- Регрессия: `GuardActionRegistryControllerIntegrationTest` (выдача реестров через API),
  `TransitionMapValidatorIntegrationTest`.

## Открытые вопросы

1. **Нужны ли коды `G-R-*`/`A-R-*`** (жизненный цикл замечаний) в спецификации — то есть
   дописать §4.6/§5.7, или считать замечания частью существующих условий и убрать коды?
2. **Пометка «кода нет»** — это временное состояние или сигнал к тому, чтобы завести коды в
   спецификации для всех реализованных условий (включая шаблоны)? От ответа зависит объём
   правки §4–5 в `doc/arch/` (это уже задача Cowork, не кода).
3. Нужна ли автоматическая проверка соответствия «код в реестре ↔ §10» при старте приложения
   (fail-fast) или достаточно теста-линтера?
