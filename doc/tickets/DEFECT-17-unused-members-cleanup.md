# DEFECT-17 — Мёртвые поля, константы и классы без потребителя

> Найден при документировании кода (PR #17). Исправление — ветка `fix/defect-17-dead-code`.
> Связанные тикеты: DEFECT-08 (поля-заглушки), DEFECT-13 (автоархивация), DEFECT-16 (миграции).

## Статус

`Не начато`

## Контекст

Часть модели и утилит не используется ни одним компонентом: поля объявлены и (иногда) заполняются,
но никто их не читает, либо код есть, а вызовов нет. Такой код удорожает чтение модели — по
полям кажется, что механизм работает, а на самом деле он не реализован — и создаёт ложные
ожидания у новых участников. Тикет приводит перечень к решению «оставить как задел с пометкой»
или «удалить».

## Источники (для истории, не для перехода)

- `03_domain_model.md` §6–7 — состав полей агрегатов.
- `08_db_schema.md` §7–11 — колонки, которые нельзя удалить без миграции.
- `04_state_machines.md` §3.2 — модель конфига (для `TransitionModel.statesByCode`).
- `05_guards_actions_registry.md` §7 — коды нарушений валидации (для `PARAMS_INVALID`).

## Что уже есть в репозитории и что не используется

Проверено поиском по `src/main` и `src/test` (совпадения только в объявлении/комментариях
означают «нет потребителя»):

| Элемент | Файл | Что найдено |
|---|---|---|
| `TransitionModel.statesByCode` | `engine/model/TransitionModel.java:39` | собирается в `ModelFactory` (строки 59, 71), но не читается нигде — модель тащит коллекцию состояний без потребителя |
| `TransitionMapValidator.validateOrThrow` | `engine/validation/TransitionMapValidator.java:226` | вызывается только из `TransitionMapValidatorIntegrationTest`; в продовом коде не используется |
| `TransitionMapErrorCode.PARAMS_INVALID` | `engine/validation/TransitionMapErrorCode.java:35` | объявлен, но не порождается: `params_schema` реестров не заполняется (см. README, «Валидация карты переходов») |
| `KnownEventTypes.sorted()` | `engine/validation/KnownEventTypes.java:92` | вызовов нет |
| `Participant.decidedAt` | `domain/process/Participant.java` | геттеров/сеттеров в коде нет (`RecordDecisionAction` пишет только `Decision`); поле никогда не заполняется |
| `StageInstance.originalOrderIdx` | `domain/process/StageInstance.java:64` | заполняется при генерации маршрута, нигде не читается |
| `EntitySnapshot.fetchedAt` | `dto/generation/EntitySnapshot.java:43` | нигде не читается |
| `ResponsibleNotResolvedException` | `exception/ResponsibleNotResolvedException.java` | не бросается нигде; связь — `ProcessInstance.responsibleUserId` не заполняется ни одним компонентом |
| `ExceptionCauses.hasIllegalArgumentCause` | `api/exception/ExceptionCauses.java:40` | не вызывается (обработчик использует `findCause` напрямую) |
| `ProcessInstance.autoArchiveScheduledAt` | `domain/process/ProcessInstance.java:144` | не заполняется (см. DEFECT-13) |
| `ProcessInstance.revisionLabel` | `domain/process/ProcessInstance.java:70` | не заполняется (ревизии документа — PHASE-26) |
| `import java.time.Duration` | `config/SchedulingConfig.java:3` | неиспользуемый импорт (единственное вхождение) |

Проверено и **не является** мёртвым (не удалять): `StageInstance.originalOrderIdx` заполняется;
`isOrderMandatory`/`orderMandatory` читаются (`TemplateMapper`, `TemplateRequestMapper`,
`RouteGenerationService`); `Participant.orderIdx` используется для последовательного
executionOrder (PHASE-27).

## Явно не входит (Out of scope)

- Удаление колонок БД (это миграция + согласование с Cowork).
- Реализация PHASE-10/26/27 (доп. согласующие, ревизии, последовательные участники).
- Поля-дубликаты участника (`decision`) — см. DEFECT-08, где решение принимается отдельно.

## Технические требования

1. Для каждого элемента из таблицы выбрать одно:
   - **удалить** (если это код без схемных последствий: `statesByCode`, `sorted()`,
     `hasIllegalArgumentCause`, `import Duration`, `validateOrThrow` — если решено, что
     транзакция публикации карты не появится);
   - **оставить с явной пометкой** «задел для PHASE-NN, не используется» в Javadoc
     (для полей, связанных со схемой: `autoArchiveScheduledAt`, `revisionLabel`,
     `originalOrderIdx`, `fetchedAt`, `ResponsibleNotResolvedException`).
2. Для `PARAMS_INVALID` — решение по существу: либо заполнять `params_schema` и реализовать
   проверку (тогда код перестаёт быть мёртвым), либо убрать код из перечисления и обновить
   README/`doc/JAVADOC_COVERAGE.md`.
3. Для полей, попавших в «оставить», Javadoc должен отвечать на два вопроса: кто заполнит и в
   какой фазе; что произойдёт, если прочитать поле сейчас. Формулировки из PR #17 сохранить и
   дополнить.
4. Удаление полей сущностей — только вместе с миграцией и отдельным согласованием; в этом
   тикете такие удаления не выполняются (только пометки).

## Критерии приёмки

1. Поиск по коду не находит неиспользуемых элементов из списка «удалить» (или для каждого
   оставшегося в Javadoc есть явная пометка «не используется, задел PHASE-NN»).
2. `./gradlew build` зелёный; удаление `validateOrThrow` не ломает
   `TransitionMapValidatorIntegrationTest` (тест обновлён осознанно) либо метод сохранён с
   пометкой и обоснованием.
3. `import java.time.Duration` удалён (компиляция не ломается).
4. `doc/JAVADOC_COVERAGE.md` §4 обновлён: пункты про мёртвый код либо закрыты, либо уточнены с
   указанием решения.
5. Ни одно изменение не требует правки применённых миграций.

## Тестирование

- Компиляция и полный набор тестов (`./gradlew build`) как основная проверка.
- Для `TransitionModel.statesByCode`: если коллекция удаляется — проверить
  `TransitionEngineUnitTest`/`TransitionEngineIntegrationTest`, что модель по-прежнему строит
  переходы (поиск по `(fromState, trigger)`).
- Для `PARAMS_INVALID`: при решении «реализовать» — тест валидатора на несоответствие параметров
  схеме; при решении «удалить» — отсутствие кода в перечислении.

## Открытые вопросы

1. **`validateOrThrow`** — точка входа будущей транзакции публикации карты (тогда оставить) или
   дубль `validate` (тогда удалить)? Нужно решение по плану публикации карт.
2. **`params_schema`** — планируется ли заполнение? Если нет, `PARAMS_INVALID` лучше убрать из
   перечисления, чтобы валидация не обещала непроверяемое.
3. **`originalOrderIdx`** — задел под отображение возврата на этап или рудимент? Если задел, то
   под какую фазу.
