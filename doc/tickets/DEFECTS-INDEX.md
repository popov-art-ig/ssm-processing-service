# Тикеты дефектов (DEFECT-NN)

**Версия:** 1.0
**Дата:** 2026-10-10
**Основание:** ревизия комментариев по всему проекту (`doc/JAVADOC_COVERAGE.md` §4), ветка
`docs/javadoc-coverage`, PR #17. Логика в той ветке не менялась — дефекты только
задокументированы в Javadoc рядом с кодом; здесь они разложены в задачи.

## Формат

Файлы `DEFECT-NN-<slug>.md` — по структуре `TEMPLATE.md`, но с двумя отличиями:

1. **Нумерация отдельная от `PHASE-NN`.** Фазы — план разработки, дефекты — исправления уже
   реализованного. Смешивать в одной нумерации нельзя: у фаз своя последовательность и
   зависимости. Предлагаемый префикс ветки для исправлений — `fix/defect-NN-<slug>`.
2. **Ссылки на `doc/arch/*.md` — рабочие.** Экспортированные копии спецификаций лежат в
   репозитории, поэтому тикет ссылается на раздел, а не копирует его целиком. Требование
   `TEMPLATE.md` «инлайнить текст спеки» появилось, когда этих копий в репозитории не было.

## Приоритеты

| Приоритет | Смысл | Тикеты |
|---|---|---|
| 🔴 Высокий | меняет исход согласования, ломает жизненный цикл или отдаёт внешнему клиенту неверный ответ | 01, 02, 03, 04, 05, 06 |
| 🟡 Средний | функциональность заявлена, но не работает или работает не по спецификации | 07, 08, 09, 10, 11, 12, 13, 14, 15 |
| 🟢 Низкий | качество данных, диагностика, консистентность документации и мёртвый код | 16, 17, 18, 19, 20 |

## Список

### 🔴 Высокий

| Тикет | Название | Ключевые классы |
|---|---|---|
| [DEFECT-01](DEFECT-01-remark-lifecycle-defects.md) | Жизненный цикл замечания: несогласованные статусы, поля-фантомы и незадействованные guards | `RemarkService`, `AllRemarksProcessedGuard`, `HasUnprocessedRemarksGuard`, `Remark` |
| [DEFECT-02](DEFECT-02-decision-aggregation-defects.md) | Агрегация решений этапа: расхождения с `evaluateAggregation` по четырём режимам | `AggregationApprovedGuard`, `AggregationApprovedWithCommentsGuard`, `AggregationReworkGuard` |
| [DEFECT-03](DEFECT-03-no-active-processes-guard.md) | Архивация шаблона: неверный список финальных статусов процессов | `NoActiveProcessesGuard` |
| [DEFECT-04](DEFECT-04-component-resolver-error-wrapping.md) | Ошибка резолвинга бина не той реализации отдаётся как 500 | `ComponentResolver` |
| [DEFECT-05](DEFECT-05-recall-precondition-and-audit.md) | Отзыв процесса без предусловия статуса и без аудита отклонённого перехода | `ProcessService.recall`, `TransitionEngine` |
| [DEFECT-06](DEFECT-06-process-publication-error-mapping.md) | Публикация шаблона и подбор: доменные ошибки отдаются как 500 вместо 4xx | `GlobalExceptionHandler`, `TemplateService`, `MatchService` |

### 🟡 Средний

| Тикет | Название | Ключевые классы |
|---|---|---|
| [DEFECT-07](DEFECT-07-stage-iteration-and-rework.md) | Статусы итераций, цикл возврата на доработку и отзыв в карте переходов | `StageIteration`, `ActivateTargetStageAction`, `EvaluateProcessReworkAction` |
| [DEFECT-08](DEFECT-08-participant-and-additional-approver-placeholders.md) | Поля-заглушки участника и рекомендаций доп. согласующего | `Participant`, `AdditionalApprover`, `Decision` |
| [DEFECT-09](DEFECT-09-adapter-stubs-in-production.md) | Боевые заглушки адаптеров сущностей и ролей | `EntityAdapterStub`, `RoleResolverAdapterStub`, `RoleResolverAdapter` |
| [DEFECT-10](DEFECT-10-snapshot-on-start-config-version.md) | Snapshot-on-Start: версия карты переходов фиксируется константой | `RouteGenerationService`, `ProcessInstance.configVersion` |
| [DEFECT-11](DEFECT-11-route-validation-gaps.md) | Генерация маршрута: обязательные слоты, операторы условий и число участников | `RouteValidatorService`, `MatchService`, `RouteGenerationService` |
| [DEFECT-12](DEFECT-12-allowed-return-stages.md) | Возврат на этап, запрещённый шаблоном (`allowedReturnStages` не читается) | `IsTargetStageAllowedGuard`, `StageInstance` |
| [DEFECT-13](DEFECT-13-workflow-diagnostics-and-completion.md) | Диагностика незавершённых и «несработавших» переходов, автоархивация | `EvaluateProcessCompletionAction`, `EvaluateProcessReworkAction`, `ActivateTargetStageAction` |
| [DEFECT-14](DEFECT-14-template-publication-lifecycle.md) | Публикация шаблона: Fork & Drain, депрекация без проверок, правка архивированного | `TemplateService` |
| [DEFECT-15](DEFECT-15-registry-code-mismatches.md) | Коды `G-*`/`A-*` в реестрах V19–V25 не совпадают со спецификацией | миграции `V19`–`V25` |

### 🟢 Низкий

| Тикет | Название | Ключевые классы |
|---|---|---|
| [DEFECT-16](DEFECT-16-migration-cleanup.md) | Чистка миграций: дубликат перехода в V23, самопротиворечивый комментарий в V20 | `V20`, `V23` |
| [DEFECT-17](DEFECT-17-unused-members-cleanup.md) | Мёртвые поля, константы и классы без потребителя | `KnownEventTypes`, `TransitionModel`, `TransitionMapValidator`, `Participant`, `Comment` и др. |
| [DEFECT-18](DEFECT-18-scheduling-and-shedlock.md) | Планировщик и Shedlock: задачи-заглушки и расхождение версий | `SchedulingConfig`, `build.gradle`, `V13` |
| [DEFECT-19](DEFECT-19-slot-priority-and-imports.md) | Приоритет ролей слота и неиспользуемый импорт | `RouteGenerationService`, `SchedulingConfig` |
| [DEFECT-20](DEFECT-20-docs-consistency.md) | Консистентность README и Javadoc с фактическим кодом | `README.md`, `StatusRegistry`, `CoordinationApplication` |

## Порядок работы

1. Тикеты можно брать по одному: каждый самодостаточен и оформляется отдельной веткой
   `fix/defect-NN-<slug>` с PR в `main`.
2. Тикеты 01 и 02 меняют исход согласования — их стоит делать первыми и с интеграционными
   тестами на сценарии, описанные в критериях приёмки.
3. Тикеты 15 и 16 правят только описания и данные миграций: применять их к уже развёрнутой БД
   нужно через **новую** миграцию (Flyway контрольные суммы применённых файлов менять нельзя).
4. После исправления каждого дефекта обновляйте соответствующий пункт в
   `doc/JAVADOC_COVERAGE.md` §4 (он играет роль реестра находок).
