# Покрытие кода комментариями (ветка `docs/javadoc-coverage`)

Отчёт о разовой работе по документированию исходного кода: было — 118 из 215 объявлений типов
с Javadoc, стало — все 214 файла `src/main/java`. Соглашение о комментариях —
[CODE_COMMENTS.md](CODE_COMMENTS.md); скрипты проверки — `doc/tools/`.

## 1. Итоговые метрики

Замер: `doc/tools/CommentCoverage.ps1`; базовая линия — `main` (`aec7ef4`), итог — рабочее
дерево ветки.

| Метрика | Было | Стало | Цель из §8 соглашения |
|---|---|---|---|
| Файлы, в которых объявление типа не документировано | 97 из 214 | **0** | — |
| Объявления типов с Javadoc | 118 / 215 (54,9 %) | **214 / 215 (99,5 %)** | ≥ 95 % |
| Методы/конструкторы с Javadoc | 26 / 177 (14,7 %) | **148 / 177 (83,6 %)** | ≥ 80 % |
| Поля класса с описанием | 29 / 380 (7,6 %) | **271 / 380 (71,3 %)** | ≥ 50 % |
| Ссылки `{@link}` с существующей целью | — | **452 / 452 (100 %)** | — |

Единственное объявление типа без Javadoc — второй тип в одном из файлов (скрипт считает
объявления, а не файлы; файлов без документации нет). Незакрытая константа —
`PreviousStageCompletedGuard.COMPLETED_STATUSES` (её смысл объяснён inline-комментарием выше).

**Почему полей «всего 380», а не 616.** Регулярное выражение скрипта ловит и локальные
переменные (`List<Comment> comments = ...` внутри метода) — их 236, и соглашение (§3) их
описывать не требует. Скрипт разделяет объявления по отступу: поля класса объявляются на
уровне тела класса (отступ 4), локальные переменные — с отступом 5 и более, и выводятся
отдельной строкой `locals/params`, не попадая в знаменатель.

**Из 109 недокументированных полей класса 108 — DI-зависимости** (`private final XxxRepository`
в 7 сервисах, 42 guard/action-бинах и контроллерах). Комментарий вида «Репозиторий процессов»
не добавляет смысла: тип уже назван в объявлении, а роль класса описана в Javadoc класса.
Оставшиеся поля сущностей, DTO и конфигураций описаны — `domain/process` 130/133,
`domain/template` 48/48, `domain/registry` 39/39, `domain/audit` 12/12,
`domain/statemachine` 24/30 (остальные — технические `created_at`/`created_by`/`updated_at`,
которые были описаны в ходе ревью этой метрики).

## 2. Что сделано по слоям

| Слой | Было | Стало | Что появилось |
|---|---|---|---|
| `engine/**`, `domain/statemachine` | 40 % типов, 1 метод | 100 % типов и методов | алгоритм перехода, порядок записи статуса, реестры, валидация карт |
| `service/**` (сервисы + guard/action) | 5 типов без Javadoc, 15 методов | 100 % типов, 95 % методов | жизненный цикл процессов/шаблонов/замечаний, коды `G-*`/`A-*` и переходы |
| `domain/**` (кроме `statemachine`) | 25 полей | 100 % типов, полей 243/245 | агрегаты, инварианты БД, «мёртвые» поля |
| `api/**` | 2 из 10 контроллеров | 100 % типов, 100 % методов | связь с сервисами, коды ответов, заглушки |
| `adapter/**`, `config/**`, `dto/**`, `exception/**` | 8 типов без Javadoc | 100 % типов | заглушки внешних интеграций и их последствия |

Всего: **209 файлов изменено, 5541 строка добавлена, 108 удалено** — только комментарии и
документация (см. §5).

## 3. Документированные решения «почему»

Самая ценная часть работы — фиксация мест, где реализация отклоняется от очевидного варианта
или от спецификации. Ключевые:

- `TransitionEngine.execute` — статус записывается **до** выполнения actions: guards вложенных
  переходов и actions закрытия этапа читают статус связанных сущностей из репозитория в той же
  транзакции; при обратном порядке следующий этап не активировался, процесс не завершался.
- `AllParticipantsDecidedGuard` / `DecisionRepository` — источник истины о голосовании — строки
  `decision`, а не `participant.status`, потому что движок меняет статус до actions.
- `MatchService.chainRoot` / `TemplateService.updateInPlace` — работа с цепочкой версий шаблона
  (ADR-023): почему побеждал самый старый шаблон и почему перед пересозданием нужен
  `entityManager.flush()`.
- `ProcessService.equalsIfPresent`, `listProcesses` — `Specification.unrestricted()` вместо `null`
  и клампинг размера страницы (находки аудита).
- `RemarkService`, `ProcessService`, `TemplateService` — явные предусловия по статусу: без них
  движок выбирает переход по `(fromState, trigger)` и, например, повторная публикация молча
  депрецировала шаблон.

## 4. Дефекты, зафиксированные в комментариях (не исправлялись)

Ветка документации не меняет логику, поэтому найденные дефекты описаны рядом с кодом. Каждый
пункт разложен в отдельный тикет по шаблону `TEMPLATE.md` — см.
[doc/tickets/DEFECTS-INDEX.md](tickets/DEFECTS-INDEX.md) (DEFECT-01…20 с приоритетами, объёмом
и критериями приёмки). Ниже — краткая сводка.

### Высокий приоритет

| # | Где | Суть |
|---|---|---|
| D1 | `AllRemarksProcessedGuard`, `HasUnprocessedRemarksGuard` | ищут статусы `Open`/`InProgress`, а `RemarkService` создаёт замечание в `Active`; статуса `Open` в `status_registry` нет — возобновление процесса проходит с необработанными замечаниями |
| D2 | `AggregationReworkGuard`, `AggregationApprovedGuard` | в режиме `ANY_APPROVE` инвертировано условие (`allMatch` вместо `anyMatch`), при `ANY_DECISION` — всегда `true`: комбинация «Согласовано + Отклонено» оставляет этап навсегда `Active` |
| D3 | `NoActiveProcessesGuard` | `Recalled` не считается финальным, а `Canceled` не соответствует ни одному коду: отозванный процесс навсегда блокирует архивацию шаблона |
| D4 | `ComponentResolver` | `BeanNotOfRequiredTypeException` не оборачивается в `ComponentResolutionException` (ловится только отсутствие бина) |
| D5 | `ProcessService.recall` | нет предусловия по статусу: из `Draft` вызов отзыва выполняет `StartProcess` |

### Средний приоритет

| # | Где | Суть |
|---|---|---|
| D6 | `IsRemarkAuthorGuard`, `IsRemarkAssigneeGuard`, `HasUnprocessedRemarksGuard` | зарегистрированы в реестре и покрыты тестами, но не входят ни в один `transition_config.guards` |
| D7 | `IsTargetStageAllowedGuard` | `allowedReturnStages` шаблона не читается — ограничение возврата задано только кодом |
| D8 | `RouteGenerationService` | `configVersion` жёстко `1` — Snapshot-on-Start (ADR-003) фактически не реализован |
| D9 | `RouteValidatorService`, `RouteGenerationService` | не соблюдены критерии 5 и 7 PHASE-15 (полнота обязательных слотов, операторы `gt`/`lt`/`contains`, один пользователь на роль вместо списка) |
| D10 | `EntityAdapterStub`, `RoleResolverAdapterStub` | боевые бины-заглушки: фиктивные атрибуты сущности и всегда пустой список ролей (шаблон с обязательным «ролевым» слотом создать нельзя) |
| D11 | `RemarkService`, домен `Remark` | `resolvedBy`/`rejectedAt`/`rejectionReason` не заполняются — причина отклонения замечания теряется |
| D12 | `StageIteration` | `status`/`completedAt` не обновляются; начальная итерация создаётся как `"Pending"`, при возобновлении — `"Active"`, строк в `status_registry` для `STAGE_ITERATION` нет |
| D13 | `TemplateService.publish` | предыдущая версия цепочки не депрецируется (Fork & Drain при публикации не реализован) |
| D14 | `GlobalExceptionHandler` | `NoMatchingTemplateException`, `TemplateNotFoundException`, `RequiredSlotNotResolvedException` отдаются как `500` вместо `4xx` |
| D15 | `GuardRegistryEntry`, `ActionRegistryEntry` и др. | 18 некорректных ссылок на коды `G-*`/`A-*` в описаниях реестров (V19–V25) не совпадают со сводной таблицей спецификации |
| D16 | `V23__seed_process_rework_resume_transitions.sql` | блок `insert into transition_config ... 'ReactivateStage'` продублирован дважды |
| D17 | `V20__seed_participant_decisions.sql` | комментарий утверждает, что V21 применяется раньше V20 (неверно для Flyway) |
| D18 | `StatusRegistry` | Javadoc ссылается на миграцию `V27__status_registry_composite_key.sql`, которой нет (составной ключ создан в V1) |

### Низкий приоритет

- «Мёртвые» поля и классы без потребителя: `Participant.decision`, `Participant.decidedAt`,
  `Participant.userEditable/organizationEditable/deletable`, `StageInstance.originalOrderIdx`,
  `orderMandatory`, `Comment.parent`, `EntitySnapshot.fetchedAt`, `KnownEventTypes.sorted()`,
  `TransitionModel.statesByCode`, `validateOrThrow`, `PARAMS_INVALID`, `ResponsibleNotResolvedException`.
- Конфигурация планировщика включена (`@EnableScheduling`, Shedlock), но ни одной `@Scheduled`-джобы нет.
- `RouteGenerationService.resolveSlot` берёт только `acceptableRoles.get(0)`; `resolveParticipants` создаёт участника на каждого кандидата роли.
- `SchedulingConfig` — неиспользуемый импорт `java.time.Duration`; версия Shedlock расходится между ADR-013 (7.7.0), комментарием V13 и `build.gradle` (6.3.0).
- `CoordinationApplication` — упоминание Gradle 8.8 исправлено на 8.14; `KripAdapter` из `09_adapters.md` §7 в коде отсутствует.
- Расхождения README: action-бины `SetDecidedAt`, `RecordAutoDecision`, переход `CreateTemplate`, guard'ы `IsValidDecisionType`, `HasCommentWhenRequired` в коде отсутствуют.

## 5. Как проверялось

| Проверка | Команда | Результат |
|---|---|---|
| Изменены только комментарии | `doc/tools/VerifyCommentsOnly.ps1 -Base main` | 201 файл, **0 файлов с изменённым кодом** |
| Компиляция main и test (javac 21, офлайн) | `doc/tools/CompileCheck.ps1 -WithTests` | 214 + 57 файлов, **OK** |
| Ссылки `{@link}` указывают на существующие цели | `doc/tools/VerifyJavadocLinks.ps1` | 452 ссылки, **0 неразрешённых** |
| Покрытие | `doc/tools/CommentCoverage.ps1` | см. §1 |

`./gradlew verifyTestCompilation` в этом окружении запустить не удалось: Gradle-обёртка не может
открыть `.lck` в `~/.gradle` (запись за пределами рабочего каталога запрещена), а запуск
распакованного дистрибутива напрямую падает на инициализации native-services. Поэтому
компиляция проверена эквивалентным способом: JDK 21 из `~/.gradle/jdks`, classpath из кэша
зависимостей, Lombok как annotation processor (скрипт `CompileCheck.ps1`). В CI следует
использовать штатный `./gradlew verifyTestCompilation`.

## 6. Инструменты

| Скрипт | Назначение |
|---|---|
| `CommentCoverage.ps1` | доля задокументированных типов, методов и полей; `-Details`, `-ByPackage`, `-ListMissingTypes` |
| `VerifyCommentsOnly.ps1` | доказывает, что diff относительно ревизии меняет только комментарии (сравнение нормализованного кода) |
| `VerifyJavadocLinks.ps1` | проверяет цели всех `{@link}` по индексу типов и членов проекта |
| `CompileCheck.ps1` | компилирует `src/main/java` (и `src/test/java` с `-WithTests`) без Gradle, сети и Docker |

Скрипты сохранены с UTF-8 BOM: Windows PowerShell 5.1 читает файлы без BOM как ANSI и ломает
разбор строк с кириллицей.
