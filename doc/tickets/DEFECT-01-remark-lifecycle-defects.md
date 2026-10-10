# DEFECT-01 — Жизненный цикл замечания: несогласованные статусы, поля-фантомы и незадействованные guards

> Найден при документировании кода (PR #17). Исправление — ветка `fix/defect-01-remark-lifecycle`.
> Связанные тикеты: DEFECT-02 (агрегация решений), DEFECT-13 (диагностика).

## Статус

`Не начато`

## Контекст

Замечание — единственная сущность, которая переживает возврат процесса на доработку и решает,
можно ли процесс возобновить: переход `ResumeProcess` (`OnRework → InProgress`) защищён
guard'ом «все замечания обработаны». Сейчас эта защита не работает: guard'ы ищут статусы,
которых в данных не бывает, поэтому процесс возобновляется с необработанными замечаниями.
Дополнительно часть жизненного цикла замечания объявлена в модели, но не заполняется, а три
guard'а зарегистрированы в реестре и не подключены ни к одному переходу.

`RemarkService` написан и работоспособен, но REST-эндпоинты замечаний — заглушки
(`RemarkController` бросает `UnsupportedOperationException`), поэтому через HTTP дефект пока
не воспроизводится; через сервисный слой и в тестах — да.

## Источники (для истории, не для перехода)

- `04_state_machines.md` §10.1–10.3 — `RemarkStateMachine`: состояния и переходы замечания.
- `05_guards_actions_registry.md` §4.1 `G-P-006 AllActiveRemarksHandled`, §4.6 — условия замечания;
  §11.3 `G-RT-002 AllActiveRemarksHandled`.
- `07_api_contract.md` §17 — контракт эндпоинтов замечаний.
- `doc/tickets/PHASE-11-remarks-lifecycle.md` — постановка жизненного цикла замечаний.

## Что уже есть в репозитории

- `src/main/java/ru/coordination/approval/service/RemarkService.java` — `createRemark`,
  `processRemark`, `rejectRemark`, `takeIntoWork`; константы `CREATE_REMARK_FROM_STATE = "Draft"`,
  `CREATE_REMARK_TO_STATE = "Active"`.
- `src/main/java/ru/coordination/approval/service/guard/AllRemarksProcessedGuard.java` —
  `guard_registry.handler = 'allRemarksProcessedGuard'`, включён в guards `ResumeProcess`.
- `src/main/java/ru/coordination/approval/service/guard/HasUnprocessedRemarksGuard.java` —
  зарегистрирован (V24), но ни в одном `transition_config.guards` не встречается.
- `src/main/java/ru/coordination/approval/domain/process/Remark.java` — поля `resolvedBy`,
  `resolvedAt`, `rejectedBy`, `rejectedAt`, `rejectionReason`, `activatedBy`.
- `src/main/resources/db/migration/V24__seed_remark_lifecycle_transitions.sql` — статусы REMARK,
  переходы, реестры.
- Тесты: `src/test/java/ru/coordination/approval/service/RemarkLifecycleIntegrationTest.java`,
  `src/test/java/ru/coordination/approval/service/guard/AllRemarksProcessedGuardTest.java`,
  `HasUnprocessedRemarksGuardTest.java`.

## Дефекты в объёме тикета

### 1. Guard'ы проверяют статусы, которых не существует

`AllRemarksProcessedGuard` и `HasUnprocessedRemarksGuard` запрашивают замечания в статусах
`Open` и `InProgress`:

```java
remarkRepository.existsByProcessIdAndStatusIn(process.getId(), List.of("Open", "InProgress"));
```

Факты:

- `RemarkService` создаёт замечание сразу в `Active` (`CREATE_REMARK_TO_STATE`), минуя `Open`;
- в `status_registry` для `entity_type = 'REMARK'` (миграция V24) статуса `Open` **нет вообще**
  (зарегистрированы `Draft`, `Active`, `InProgress`, `Processed`, `Rejected`, `Proposed`,
  `NotRequired`);
- спецификация `05_guards_actions_registry.md` §4.1 `G-P-006` требует проверять **`Active` или
  `InProgress`**.

Следствие: действующее замечание в статусе `Active` невидимо → `AllRemarksProcessedGuard`
возвращает `true` → процесс возобновляется с необработанными замечаниями;
`HasUnprocessedRemarksGuard` всегда возвращает `false`.

### 2. Расхождение модели статусов замечания со спецификацией

`04_state_machines.md` §10.1 описывает состояния `Proposed`, `Active`, `InProgress`,
`NotRequired`, `AutoProcessed`, `Resolved`, `Rejected`, `Closed` и переходы §10.2
(`ActivateRemark`, `MarkNotRequired`, `AutoProcessRemark`, `TakeIntoWork`, `ResolveRemark`,
`RejectRemark`, `CloseRemark`). В V24 зарегистрированы `Draft`, `Active`, `InProgress`,
`Processed`, `Rejected`, `Proposed`, `NotRequired` и переходы `CreateRemark`,
`StartProcessingRemark`, `ProcessRemark`, `RejectRemark`, `MarkNotRequired`.

Расхождения: нет `AutoProcessed`, `Resolved`, `Closed` и переходов `ResolveRemark`,
`CloseRemark`, `AutoProcessRemark`; появились не описанные в спецификации `Draft` и `Processed`.

**Требуется решение Cowork:** какой набор считать источником истины. Тикет исходит из того, что
источник истины — спецификация §10, а `Draft`/`Processed` — временные коды реализации, которые
надо привести к §10 (либо зафиксировать отступление аддендумом к ADR и поправить спецификацию).

### 3. Поля результата обработки не заполняются

`resolvedBy`, `resolvedAt`, `rejectedBy`, `rejectedAt`, `rejectionReason` не пишет ни один
компонент. `RemarkService.rejectRemark(reason)` передаёт причину в параметр перехода, где её
читает `IsRemarkRejectionGuard`, после чего причина теряется — в БД её нет, а `NotifyRemarkStatusChangeAction`
пуст. Восстановить, почему замечание отклонили, по данным невозможно; `RemarkDto` (§17) не
может вернуть причину.

### 4. Зарегистрированные guards не подключены к переходам

`IsRemarkAuthorGuard` (`G-R-002` по реестру), `IsRemarkAssigneeGuard` (`G-R-003`),
`HasUnprocessedRemarksGuard` есть в `guard_registry` (V24) и покрыты юнит-тестами, но поиск по
`transition_config.guards` во всех миграциях их не находит. Описание в V24 («используется в
переходах `ProcessRemark` и `RejectRemark`») не соответствует карте: там только
`IsRemarkResolution` и `IsRemarkRejection`. Авторизация обработки замечания фактически
отсутствует (`IsParticipantGuard` — заглушка, всегда `true`).

### 5. Нет авто-закрытия замечаний при закрытии этапа

`04_state_machines.md` §10.2 содержит переход `CloseRemark` (`systemAction`,
guard `ApproveOrApproveWithComments`) и `AutoProcessRemark` (`Proposed → AutoProcessed`,
guard `StageClosed`); критерий приёмки 8 PHASE-11 требует `AutoProcessOpenRemarks`. В карте V24
таких переходов и действий нет.

## Явно не входит (Out of scope)

- Реализация REST-эндпоинтов замечаний (`RemarkController` — 5 заглушек). Это отдельная работа,
  см. `doc/REST_API_COMPLETION.md`; после неё дефект станет воспроизводим по HTTP.
- `AdditionalApprover`/PHASE-10 — только в части, где замечание связано с доп. согласующим.
- Перенос причины отклонения в события (`NotifyRemarkStatusChangeAction`) — PHASE-19.

## Технические требования

1. Заменить набор статусов «необработанное замечание» на актуальный по §10 (минимум
   `Active` + `InProgress`, а после решения по п. 2 — согласованный список). Список вынести в
   одну константу, чтобы guards и авто-закрытие не разошлись снова; по возможности — читать
   признак терминальности из `status_registry` (`is_terminal`), а не дублировать строки.
2. `RemarkService.createRemark` должен создавать замечание в статусе, предусмотренном картой
   переходов (сейчас `Active` ставится в обход перехода `CreateRemark`, оставляя
   `CREATE_REMARK_FROM_STATE = "Draft"` без применения).
3. Заполнять поля результата: при обработке — `resolvedBy`/`resolvedAt` (переход
   `ProcessRemark`/`ResolveRemark`), при отклонении — `rejectedBy`/`rejectedAt`/`rejectionReason`.
   Причину брать из параметра перехода `reason`.
4. Подключить `IsRemarkAuthorGuard`/`IsRemarkAssigneeGuard` (или осознанно удалить их из реестра,
   зафиксировав решение) к переходам обработки и отклонения замечания; заменить
   `IsParticipantGuard`-заглушку реальной проверкой участия в процессе.
5. Добавить переходы/действия `CloseRemark` и `AutoProcessRemark` **либо** оформить отступление
   аддендумом и вынести в отдельную фазу — решение фиксируется в PR.

## Критерии приёмки

1. Процесс с замечанием в актуальном «необработанном» статусе **не возобновляется**:
   `ProcessService.resume` возвращает отказ (`TransitionResult.notPerformed`), статус процесса
   остаётся `OnRework`. Интеграционный тест на реальном Postgres (Testcontainers).
2. После перевода всех замечаний в терминальные статусы возобновление проходит, следующий этап
   активируется.
3. `HasUnprocessedRemarksGuard` возвращает `true` при действующем замечании и `false` после
   его закрытия — при условии, что guard подключён к переходу (п. 4) либо удалён из реестра.
4. При отклонении замечания `rejectionReason`, `rejectedBy`, `rejectedAt` заполнены; при
   обработке — `resolvedAt`/`resolvedBy`. Проверяется запросом к БД либо через
   `RemarkRepository`.
5. Ни один guard/action замечания не хранит список статусов локально: изменение набора
   статусов — правка в одном месте.
6. `./gradlew verifyTestCompilation` и `./gradlew build` зелёные.

## Тестирование

- Юнит: `AllRemarksProcessedGuardTest`, `HasUnprocessedRemarksGuardTest` — дополнить кейсами
  `Active`, `InProgress`, `Rejected`, `Processed`/`Resolved` (по итоговому словарю статусов).
- Интеграционные (Testcontainers): сценарий «создать замечание → попытка возобновления
  (ожидаем отказ) → обработать замечание → возобновление проходит»; сценарий «отклонить
  замечание с причиной → причина сохранена».
- Регрессия: `RemarkLifecycleIntegrationTest`, `ProcessResumeIntegrationTest`.

## Открытые вопросы

1. **Источник истины по статусам замечания:** `04_state_machines.md` §10 или фактический набор
   V24 (`Draft/Active/InProgress/Processed/Rejected`)? От этого зависит объём правки карты и
   необходимость аддендума к ADR. Решение принимает Cowork.
2. **Кто обрабатывает замечание:** автор замечания, инициатор процесса или любой участник?
   `G-R-002`/`G-R-003` описывают автора и ответственного, но не подключены; `IsTargetStageAllowedGuard`
   для возврата проверяет инициатора (`G-P-001`).
3. **Нужны ли `IsRemarkAuthorGuard`/`IsRemarkAssigneeGuard`** в текущем жизненном цикле или они
   задел под PHASE-10 (доп. согласующие)?
