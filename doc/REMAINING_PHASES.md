# Оставшиеся фазы разработки

**Версия:** 1.0  
**Дата:** 2026-09-21  
**Статус реализации:** PHASE-01 до PHASE-04 завершены

---

## Контекст

Модуль «Согласование» реализуется поэтапно. На данный момент завершены фазы 1-4:
- **PHASE-01**: Базовая инфраструктура (схема БД, 27 таблиц, доменные сущности)
- **PHASE-02**: State Machine Engine (TransitionEngine, guards/actions registry, ModelFactory)
- **PHASE-03**: ProcessService.startProcess (переход StartProcess для STANDARD)
- **PHASE-04**: Активация первого этапа (ActivateStage, AssignStageTasks)

**Текущее состояние:** процесс можно запустить (Draft → InProgress), первый этап активируется (Pending → Active), участникам назначаются задачи, но **решения принимать нельзя** — дальше процесс не двигается.

Этот документ описывает логический порядок оставшихся фаз для завершения функциональности модуля.

---

## Принципы планирования фаз

1. **Вертикальные срезы**: каждая фаза — минимальный сквозной путь через весь стек (domain → engine → service), а не горизонтальное покрытие всех guards/actions сразу.
2. **STANDARD первым**: тип процесса STANDARD реализуется раньше UNIFIED (проще, без финального решения).
3. **Счастливый путь перед граничными случаями**: сначала основной флоу (согласование → завершение), потом возвраты/отзывы/ревизии.
4. **Domain Core перед интеграциями**: сначала полная бизнес-логика (guards/actions/сервисы), потом REST API, Event Publisher, адаптеры.
5. **Testability**: каждая фаза тестируется интеграционно (Testcontainers) на реальной БД и реальных миграциях.

---

## Группа A: Завершение базового флоу STANDARD (принятие решений и закрытие этапов)

Цель: довести один процесс STANDARD от создания до финального статуса через счастливый путь.

### PHASE-05: Принятие решения участником (Decide, ParticipantStateMachine)

**Объём:**
- `ParticipantStateMachine` (переходы `AssignTask`, `StartWork`, `Decide`, `RevokeTask`)
- Guards: `IsAssignedParticipant` (G-PA-001), `ParticipantHasDecision` (G-PA-002)
- Actions: `RecordDecision` (A-PA-001), `RecordComment` (A-PA-002), `UpdateParticipantStatus` (A-PA-003)
- `DecisionService.decide(processId, stageId, participantId, decision)` — use-case метод
- Таблицы `decision`, `comment` используются напрямую через репозитории
- Три типа решения: Approve, ApproveWithComments, Reject
- Интеграционный тест: участник принимает решение → `participant.status = Decided`, создана запись `decision`

**Зависимости:** PHASE-04  
**Не входит:** агрегация решений (этап остаётся Active), автосогласование по срокам, Sequential execution order (назначение следующего участника)

---

### PHASE-06: Агрегация решений и закрытие этапа (evaluateAggregation)

**Объём:**
- Guards агрегации: `AllParticipantsDecided` (G-S-002), `AggregationApproved` (G-S-003), `AggregationApprovedWithComments` (G-S-004), `AggregationRework` (G-S-005)
- Переходы `StageStateMachine`: `StageApproved`, `StageApprovedWithComments`, `StageOnRework`
- Action: `EvaluateAggregation` (A-S-004) — программно запускает один из трёх переходов этапа по результату агрегации
- Логика агрегации по `decisionMode`: AND, ANY_APPROVE, ANY_REJECT, ANY_DECISION, FIRST_REJECT_FAIL_FAST
- Интеграционный тест: все участники этапа решили → этап закрывается в соответствующий статус (Approved/ApprovedWithComments/OnRework)

**Зависимости:** PHASE-05  
**Не входит:** активация следующего этапа (процесс остаётся InProgress, но этап закрыт), возврат на доработку при OnRework

---

### PHASE-07: Активация следующего этапа и завершение процесса

**Объём:**
- Action: `ActivateNextStage` (A-S-006) — находит следующий по orderIdx этап, запускает `ActivateStage` для него
- Переходы процесса: `ProcessApproved`, `ProcessApprovedWithComments` (Draft → Approved/ApprovedWithComments) — когда последний этап согласован
- Guard: `AllStagesCompleted` (G-P-006)
- Интеграционный тест полного цикла: StartProcess → первый этап → все решили → этап закрыт → второй этап активирован → все решили → процесс завершён в статусе Approved/ApprovedWithComments

**Зависимости:** PHASE-06  
**Не входит:** OnRework (возврат на доработку — следующая фаза)

---

## Группа B: Возврат на доработку и возобновление (STANDARD)

### PHASE-08: Возврат на доработку и возобновление процесса (ResumeProcess)

**Объём:**
- Переход `ProcessRework` (InProgress → OnRework) — когда один из этапов получил статус OnRework
- `ProcessService.resume(processId, targetStageId, actorId)` — выбор целевого этапа из `allowedReturnStages`
- Переход `ResumeProcess` (OnRework → InProgress)
- Actions: `ActivateTargetStage` (A-RT-005), `RejectStagesFrom` (A-RT-002) — присваивает статус Rejected этапам между целевым и текущим
- Guards: `AllRemarksProcessed` (G-P-008), `IsTargetStageAllowed` (G-P-009)
- Логика создания новых итераций: `StageIteration` с увеличенным `iterationIdx`, клонирование участников/доп.согласующих по правилам (кто назначил)
- Интеграционный тест: этап отклонён → процесс OnRework → инициатор выбирает целевой этап → новая итерация создана, этап активирован

**Зависимости:** PHASE-06 (нужен OnRework статус этапа), замечания базово (может быть минимальная заглушка)  
**Не входит:** полный lifecycle замечаний (можно упростить — проверять только отсутствие Active/InProgress)

---

### PHASE-09: Отзыв и возобновление после отзыва (RecallProcess)

**Объём:**
- `ProcessService.recall(processId, actorId)` — отзыв инициатором
- Переход `RecallProcess` (InProgress | OnRework → Recalled)
- Guards: `IsInitiator` (уже есть), `ProcessNotFinalized` (G-P-010)
- Actions: `RevokeActiveTasks` (A-P-005), `CancelActiveStages` (A-P-006)
- Переход `TakeBackInWork` (Recalled → Draft) — взятие в работу после отзыва
- Интеграционный тест: процесс отозван → все активные задачи отозваны, этапы отменены → процесс Recalled → взят в работу → Draft

**Зависимости:** PHASE-07 (нужен базовый флоу)  
**Не входит:** UNIFIED (у него отдельные правила отзыва)

---

## Группа C: Дополнительные согласующие и замечания (STANDARD)

### PHASE-10: Дополнительные согласующие (AdditionalApprover)

**Объём:**
- `AdditionalApproverService.add(processId, stageId, participantId, additionalApproverId, deadline)`
- `AdditionalApproverStateMachine` (переходы `AssignAdditionalApprover`, `RecordRecommendation`, `RevokeAdditionalApprover`)
- Таблица `additional_approver` используется напрямую
- Иерархия: доп.согласующий может назначить других доп.согласующих (без ограничения глубины)
- Видимость: сквозная по всему процессу (не изолирована итерацией)
- Guards: `IsMainApproverOrAdditional` (G-AA-001), `DeadlineBeforeStageDeadline` (G-AA-002)
- Интеграционный тест: основной участник назначает доп.согласующего → доп.согласующий даёт рекомендацию → статус этапа не меняется

**Зависимости:** PHASE-05 (нужно принятие решений)  
**Не входит:** влияние на агрегацию (доп.согласующие не влияют на статус этапа по определению)

---

### PHASE-11: Замечания и комментарии (Remark, Comment)

**Объём:**
- `RemarkService.create/activate/resolve/reject/close` — полный lifecycle
- `CommentService.create` — информационные сообщения
- `RemarkStateMachine` (переходы `CreateRemark`, `ActivateRemark`, `MarkNotRequired`, `ResolveRemark`, `RejectRemark`, `CloseRemark`, `AutoProcessRemark`)
- Guards: `IsRemarkAuthor` (G-R-001), `IsInitiator` (переиспользование), `StageNotClosed` (G-R-002)
- Actions: `NotifyInitiator` (A-R-001, событие), `AutoProcessOpenRemarks` (A-R-002) — при закрытии этапа
- Интеграционный тест: участник создаёт замечание → инициатор обрабатывает (Resolve/Reject) → при возобновлении процесса проверяется `AllRemarksProcessed`

**Зависимости:** PHASE-05 (замечания создаются при решениях)  
**Интеграция с:** PHASE-08 (блокировка возобновления при необработанных замечаниях)

---

## Группа D: Единый процесс (UNIFIED)

### PHASE-12: UNIFIED — согласование маршрута ответственным

**Объём:**
- `ProcessService.submitRoute(processId, actorId)` — отправка маршрута ответственному
- Переход `SubmitRoute` (Draft → PendingResponsibleApproval)
- `ResponsibleService.approveRoute/rejectRoute(processId, actorId)`
- Переходы `ApproveRoute` (PendingResponsibleApproval → InProgress), `RejectRoute` (PendingResponsibleApproval → Draft)
- Guards: `IsResponsible` (G-UN-001), `RouteValid` (G-UN-002)
- Actions: `SetPendingResponsibleApproval` (A-UN-001), `ActivateFirstStage` (переиспользование AssignStageTasks)
- Интеграционный тест: инициатор отправляет маршрут → ответственный согласовывает → процесс InProgress → первый этап активирован

**Зависимости:** PHASE-07 (нужна активация этапов)  
**Не входит:** финальное решение (следующая фаза)

---

### PHASE-13: UNIFIED — финальное решение ответственного

**Объём:**
- Переход `AwaitFinalDecision` (InProgress → AwaitingFinalDecision) — когда все этапы пройдены
- Guard: `AllStagesCompleted` (переиспользование)
- `FinalDecisionService.recordFinalDecision(processId, decision, actorId)`
- `FinalDecisionStateMachine` (переходы `RecordFinalDecision`)
- Переходы процесса: `FinalApprove`, `FinalApproveWithComments`, `FinalReject` (AwaitingFinalDecision → Approved/ApprovedWithComments/Rejected)
- Таблица `final_decision` используется напрямую
- Guards: `IsResponsible`, `FinalDecisionPending` (G-FD-001)
- Интеграционный тест полного цикла UNIFIED: SubmitRoute → ApproveRoute → все этапы прошли → AwaitingFinalDecision → ответственный принимает решение → процесс Approved/Rejected

**Зависимости:** PHASE-12, PHASE-07  
**Не входит:** отзыв UNIFIED (можно добавить в эту же фазу или отдельно)

---

## Группа E: Создание процессов из шаблонов

### PHASE-14: Шаблоны маршрутов (Template CRUD, версионирование)

**Объём:**
- `TemplateService.create/update/publish/deprecate/archive`
- Таблицы `template`, `stage_template`, `slot_template` используются через репозитории
- `TemplateStateMachine` (Draft → Published → Deprecated → Archived)
- Guards: `NoActiveProcesses` (G-T-001), `AllMandatorySlotsValid` (G-T-002)
- Версионирование: новая версия = новая строка с увеличенным `version`
- Fork & Drain: старая версия → Deprecated, но активные процессы доживают на ней
- Интеграционный тест: создание шаблона → публикация → проверка валидности (обязательные этапы, сроки > 0)

**Зависимости:** PHASE-04 (нужно понимание структуры маршрута)  
**Не входит:** подбор шаблона, генерация маршрута (следующая фаза), REST API для UI

---

### PHASE-15: Подбор шаблона и генерация маршрута (MatchService, RouteGeneratorService)

**Объём:**
- `MatchService.findMatchingTemplates(entitySnapshot)` — подбор по типу/подтипу/атрибутам сущности
- `RouteGeneratorService.generateRoute(templateId, entitySnapshot)` — создание ProcessInstance со всеми stages/iterations/participants
- Резолвинг слотов: конкретный пользователь имеет приоритет, роль резолвится через адаптер (stub на этой фазе)
- `RouteValidatorService.validate(process)` — проверка обязательных слотов, сроков
- Snapshot-on-Start: фиксация `configVersion` на процессе
- Интеграционный тест: снимок сущности → подбор шаблона → генерация маршрута → ProcessInstance создан со всеми участниками, готов к запуску

**Зависимости:** PHASE-14  
**Не входит:** адаптер ролей (stub возвращает пустой результат), REST API `POST /processes`

---

## Группа F: Автоматизация и планирование

### PHASE-16: Автосогласование по срокам (Timer triggers)

**Объём:**
- Планировщик (Spring @Scheduled или Quartz) проверяет просроченные задачи
- Переход `AutoApproveParticipant` (trigger = TIMER) — для участника
- Переход `AutoApproveStage` (trigger = TIMER) — для этапа (когда все участники либо решили, либо автосогласованы)
- Guards: `DeadlineExpired` (G-PA-003), `ParticipantNotDecided` (G-PA-004)
- Actions: `RecordAutoApproval` (A-PA-004), `EvaluateAggregation` (переиспользование)
- Интеграционный тест: участник не принял решение, срок истёк → планировщик запускает автосогласование → participant.status = AutoApproved, decision создан

**Зависимости:** PHASE-06 (нужна агрегация)  
**Не входит:** напоминания (следующая фаза), сложная логика рабочих дней (можно упростить до календарных дней)

---

### PHASE-17: Напоминания (Reminders, настройки периодичности)

**Объём:**
- Планировщик отправляет события-напоминания участникам о просроченных задачах
- Напоминания инициатору о долгом нахождении в статусе OnRework
- `NotificationSettings` (таблица `notification_settings`, ADR-027) — периодичность настраивается администратором
- REST API: `GET /admin/notification-settings`, `PUT /admin/notification-settings`
- Guards: `ReminderIntervalValid` (проверка > 0)
- Actions: `ScheduleReminder` (A-N-001), `SendReminder` (A-N-002) — публикация события
- Интеграционный тест: задача просрочена → через заданный интервал отправлено событие-напоминание

**Зависимости:** PHASE-16 (логично после автосогласования), Event Publisher (события должны куда-то публиковаться — либо stub, либо реальный Outbox)  
**Не входит:** реальная доставка уведомлений (внешний модуль)

---

### PHASE-18: Автоархивация процессов (ScheduleAutoArchive)

**Объём:**
- Планировщик проверяет завершённые процессы (Approved/ApprovedWithComments/Rejected/Recalled)
- Переход `AutoArchive` (trigger = TIMER) — через 180 дней после `completedAt`
- Guard: `ArchiveDeadlineExpired` (G-P-011)
- Actions: `ArchiveProcess` (A-P-007), `RevokeActiveTasks` (переиспользование)
- `AdminService.restore(processId)` — восстановление из архива (только администратор)
- Интеграционный тест: процесс завершён → completedAt + 180 дней → планировщик архивирует → процесс Archived

**Зависимости:** PHASE-07 (нужны завершённые процессы)  
**Не входит:** физическое удаление (не предусмотрено — бессрочное хранение), REST API для администраторов

---

## Группа G: Интеграции и публикация событий

### PHASE-19: Event Publisher (Outbox pattern, RabbitMQ)

**Объём:**
- `OutboxService.publish(events)` — запись в таблицу `outbox_event`
- Action: `PublishDomainEvent` (A-P-010) — добавление в `emits` уже реализовано в TransitionEngine, теперь реальная публикация
- Поллер (Spring @Scheduled) читает `outbox_event`, публикует в RabbitMQ, отмечает как `PUBLISHED`
- Retry логика для failed events
- Интеграционный тест: переход выполнен → событие записано в outbox → поллер опубликовал в RabbitMQ (Testcontainers RabbitMQ)

**Зависимости:** все domain-фазы (события генерируются во всех переходах)  
**Не входит:** подписчики событий (внешние системы), сложная логика retry (можно упростить)

---

### PHASE-20: Адаптеры (EntityAdapter, RoleResolverAdapter, PermissionAdapter)

**Объём:**
- `EntityAdapter` (реальный) — получение снимка сущности из системы-владельца (REST/gRPC клиент)
- `RoleResolverAdapter` (переход от stub к реальному) — резолвинг ролей в конкретных пользователей
- `PermissionAdapter` (stub → реальный) — проверка полномочий (КРИП)
- Конфигурация через `application.yml`: URLs внешних систем, таймауты, retry
- Guards, использующие адаптеры: `UserHasPermission` (G-P-012), `RoleResolved` (G-P-013)
- Интеграционный тест с mock-сервером (WireMock): адаптер вызывает внешний endpoint → получает ответ

**Зависимости:** PHASE-15 (адаптер ролей нужен при генерации маршрута)  
**Не входит:** реальные системы-владельцы (тестируется на mock'ах), Substitution адаптер (убран целиком, ADR-024)

---

## Группа H: REST API

### PHASE-21: REST API — группа «Процессы» (POST /processes, GET /processes/{id}, etc.)

**Объём:**
- `ProcessController` — все эндпоинты группы 12 из `07_api_contract.md`
- `POST /processes` — создание процесса из снимка сущности (вызывает MatchService + RouteGeneratorService)
- `GET /processes/{id}` — получение процесса
- `POST /processes/{id}/start` — запуск процесса (вызывает ProcessService.startProcess)
- `POST /processes/{id}/recall` — отзыв
- `POST /processes/{id}/resume` — возобновление с выбором целевого этапа
- `GET /processes` — список процессов с пагинацией/фильтрацией
- DTO-мапперы (ProcessDto, StageDto, etc.), валидация запросов
- Обработка ошибок: 400 (валидация), 403 (авторизация), 404 (не найден), 409 (конфликт состояния)
- Интеграционный тест (MockMvc): вызов API → проверка статус-кода и response body

**Зависимости:** PHASE-15 (создание процессов), PHASE-07/PHASE-08/PHASE-09 (управление процессами)  
**Не входит:** остальные группы API (этапы, решения, замечания — в следующих фазах), аутентификация (на уровне gateway)

---

### PHASE-22: REST API — группа «Решения» (POST /processes/{id}/stages/{stageId}/participants/{participantId}/decide)

**Объём:**
- `DecisionController` — эндпоинты группы 16 из `07_api_contract.md`
- `POST .../decide` — принятие решения участником
- `GET .../decisions` — список решений по процессу/этапу
- DTO: DecisionDto, DecisionRequest
- Интеграционный тест (MockMvc): участник принимает решение через API → проверка статуса этапа

**Зависимости:** PHASE-05 (DecisionService), PHASE-21 (базовая структура API)  
**Не входит:** агрегированные эндпоинты для Front (группа 25 — отдельная фаза)

---

### PHASE-23: REST API — группа «Замечания и комментарии»

**Объём:**
- `RemarkController`, `CommentController` — эндпоинты групп 17, 18 из `07_api_contract.md`
- `POST .../remarks` — создание замечания
- `PUT .../remarks/{remarkId}/resolve` — исправление
- `PUT .../remarks/{remarkId}/reject` — отклонение
- `POST .../comments` — создание комментария
- Интеграционный тест: полный lifecycle замечания через API

**Зависимости:** PHASE-11 (RemarkService), PHASE-21  
**Не входит:** файловые вложения в замечаниях (можно упростить до текста)

---

### PHASE-24: REST API — группа «Шаблоны и администрирование»

**Объём:**
- `TemplateController` — CRUD шаблонов (группа 10 из `07_api_contract.md`)
- `StateMachineConfigController` — управление картами переходов (группа 23)
- `GuardActionRegistryController` — просмотр реестров (группа 24)
- `AdminController` — восстановление из архива, настройки уведомлений (группы 22, 26)
- Интеграционный тест: создание/публикация шаблона через API → подбор шаблона работает

**Зависимости:** PHASE-14 (TemplateService), PHASE-18 (архивация), PHASE-17 (настройки)  
**Не входит:** UI для администраторов (отдельный фронтенд), сложная авторизация (достаточно проверки роли администратора)

---

### PHASE-25: REST API — агрегированные эндпоинты для Front (группа 25)

**Объём:**
- `ApprovalViewController` — готовые модели для UI без множественных запросов
- `GET /entities/{entityId}/approval-view` — процесс + этапы + участники + решения + замечания в одном запросе
- `GET /tasks/my` — задачи текущего пользователя (что нужно согласовать)
- `GET /processes/my` — процессы текущего пользователя (инициатор/участник/наблюдатель)
- DTO: ApprovalViewDto, TaskDto (агрегированные, денормализованные для UI)
- Интеграционный тест: один запрос возвращает все данные для отрисовки UI

**Зависимости:** все предыдущие API-фазы (21-24), domain-логика полностью реализована  
**Не входит:** GraphQL (если планируется), real-time updates (WebSocket)

---

## Группа I: Дополнительные возможности

### PHASE-26: Ревизии документа (процессы-ревизии)

**Объём:**
- `ProcessService.createRevision(originalProcessId, revisionLabel, interruptOriginal)`
- Создание нового процесса, связанного через `documentAggregateId`
- Guard: `ProcessIterationEnabled` (G-P-014) — проверяет флаг `processIterationEnabled` шаблона
- Actions: `InterruptOriginalProcess` (A-P-011) — если `interruptOriginal = true`
- Событие `approval.process.revision-created`
- Интеграционный тест: процесс OnRework → инициатор создаёт ревизию → новый процесс создан, старый прерван (опционально)

**Зависимости:** PHASE-08 (возврат на доработку), PHASE-15 (генерация маршрута)  
**Не входит:** UI для выбора опции (достаточно API), ограничения на количество ревизий

---

### PHASE-27: Sequential execution order (назначение следующего участника)

**Объём:**
- Action: `AssignNextParticipantTask` (A-S-009) — при `executionOrder = Sequential` после решения участника
- Guard: `PreviousParticipantDecided` (G-S-007) — следующий участник не может решить, пока не решил предыдущий
- Переход `AssignNextTask` (ParticipantStateMachine) — Pending → Assigned для следующего по orderIdx
- Интеграционный тест: этап с Sequential → первый участник решил → второй получил задачу (Pending → Assigned)

**Зависимости:** PHASE-05 (принятие решений), PHASE-04 (AssignParticipantTasks уже поддерживает Sequential для первого участника)  
**Не входит:** параллельно-последовательные комбинации (не предусмотрено спекой)

---

### PHASE-28: Дополнительные guards и actions (расширение реестра)

**Объём:**
- Реализация оставшихся guards/actions из `05_guards_actions_registry.md`, которые не вошли в предыдущие фазы
- Примеры: `UserInOrganization` (G-P-015), `EntityAttributeMatch` (G-P-016), `LogAuditDetails` (A-P-012)
- Покрытие редких граничных случаев и специфичных бизнес-правил
- Юнит-тесты на каждый guard/action

**Зависимости:** все domain-фазы (некоторые guards зависят от полной функциональности)  
**Не входит:** кастомные guards/actions проектного уровня (расширяются пользователем через plugin mechanism, если планируется)

---

## Сводная таблица приоритетов

| Группа | Фазы | Приоритет | Почему важно |
|--------|------|-----------|--------------|
| **A: Базовый флоу STANDARD** | 05-07 | **Критический** | Без этого модуль не работает вообще — процесс не завершается |
| **B: Возврат на доработку** | 08-09 | **Высокий** | Ключевая возможность STANDARD (итерации) |
| **C: Доп.согласующие и замечания** | 10-11 | **Высокий** | Обязательная часть согласования, блокирует возобновление |
| **D: UNIFIED** | 12-13 | **Высокий** | Второй тип процесса, равнозначный STANDARD |
| **E: Шаблоны и генерация** | 14-15 | **Средний** | Пока можно создавать процессы вручную в тестах, но для прода обязательно |
| **F: Автоматизация** | 16-18 | **Средний** | Автосогласование и архивация — не блокируют основной флоу, но требуются продом |
| **G: Интеграции** | 19-20 | **Средний** | Event Publisher — для уведомлений, адаптеры — для резолвинга ролей |
| **H: REST API** | 21-25 | **Средний-Низкий** | Domain-логика работает без API, но для UI необходимо |
| **I: Дополнительно** | 26-28 | **Низкий** | Расширения и редкие кейсы |

---

## Рекомендуемый порядок реализации

### Критический путь (MVP для прода):
1. **PHASE-05** → **PHASE-06** → **PHASE-07** — завершить счастливый путь STANDARD
2. **PHASE-08** — возврат на доработку (без этого STANDARD не полон)
3. **PHASE-11** — замечания (блокируют возобновление)
4. **PHASE-14** → **PHASE-15** — шаблоны и генерация (иначе процессы только тестовые)
5. **PHASE-19** — Event Publisher (для уведомлений и интеграций)
6. **PHASE-21** — базовый REST API (для UI)

### Расширенный функционал:
7. **PHASE-12** → **PHASE-13** — UNIFIED
8. **PHASE-10** — дополнительные согласующие
9. **PHASE-16** → **PHASE-17** — автосогласование и напоминания
10. **PHASE-09** — отзыв процесса
11. **PHASE-22** → **PHASE-23** — REST API для решений и замечаний
12. **PHASE-18** — автоархивация
13. **PHASE-20** — адаптеры (когда есть реальные внешние системы)
14. **PHASE-24** → **PHASE-25** — REST API администрирования и агрегированные эндпоинты
15. **PHASE-26** → **PHASE-27** → **PHASE-28** — дополнительные возможности

---

## Оценка трудозатрат (ориентировочно)

| Фаза | Сложность | Оценка (story points) | Примечание |
|------|-----------|----------------------|------------|
| PHASE-05 | Средняя | 5 | Базовая механика решений |
| PHASE-06 | Высокая | 8 | Агрегация — сложная логика по режимам |
| PHASE-07 | Средняя | 5 | Активация следующего этапа и завершение |
| PHASE-08 | Высокая | 13 | Возврат — много граничных случаев, создание итераций |
| PHASE-09 | Низкая | 3 | Отзыв — простые переходы |
| PHASE-10 | Средняя | 5 | Иерархия доп.согласующих |
| PHASE-11 | Средняя | 8 | Lifecycle замечаний — много состояний |
| PHASE-12 | Средняя | 5 | UNIFIED маршрут |
| PHASE-13 | Средняя | 5 | Финальное решение |
| PHASE-14 | Высокая | 13 | Шаблоны — CRUD + версионирование + валидация |
| PHASE-15 | Очень высокая | 21 | Генерация маршрута — самая сложная фаза (matching + резолвинг + валидация) |
| PHASE-16 | Средняя | 8 | Планировщик + автосогласование |
| PHASE-17 | Низкая | 3 | Напоминания — расширение планировщика |
| PHASE-18 | Низкая | 3 | Автоархивация — расширение планировщика |
| PHASE-19 | Средняя | 8 | Outbox + поллер + RabbitMQ |
| PHASE-20 | Средняя | 8 | Адаптеры — REST-клиенты + retry |
| PHASE-21 | Средняя | 8 | REST API процессов |
| PHASE-22 | Низкая | 3 | REST API решений |
| PHASE-23 | Низкая | 3 | REST API замечаний |
| PHASE-24 | Средняя | 5 | REST API администрирования |
| PHASE-25 | Средняя | 5 | Агрегированные эндпоинты |
| PHASE-26 | Низкая | 3 | Ревизии |
| PHASE-27 | Низкая | 3 | Sequential execution |
| PHASE-28 | Средняя | 5 | Дополнительные guards/actions |

**Итого:** ~155 story points (~25-30 недель разработки для одного разработчика, ~10-15 недель для команды из 2-3 человек)

---

## Открытые вопросы для планирования

1. **Какой MVP считается достаточным для первого релиза?**
   - Только STANDARD счастливый путь (фазы 05-07)?
   - STANDARD с возвратом на доработку (+ фаза 08)?
   - Оба типа процессов STANDARD + UNIFIED (+ фазы 12-13)?

2. **Приоритет REST API vs domain-логика:**
   - Реализовывать API сразу после каждой domain-фазы (параллельно)?
   - Или сначала вся domain-логика, потом вся API разом?

3. **Event Publisher: когда?**
   - Реализовать рано (после PHASE-07), чтобы все последующие фазы уже публиковали события?
   - Или отложить на потом, пока domain-логика стабилизируется?

4. **Адаптеры: stub или реальные?**
   - Оставлять stub'ы до момента, когда есть реальные внешние системы?
   - Или реализовать универсальные REST-клиенты заранее?

5. **Тестирование:**
   - Покрытие интеграционными тестами каждой фазы (как сейчас) — продолжать?
   - Или добавить E2E тесты через REST API после группы H?

---

## Следующие шаги

1. Согласовать приоритеты и MVP с заказчиком/владельцем продукта
2. Выбрать следующую фазу для реализации (рекомендация: **PHASE-05**)
3. Cowork подготовит детальный тикет `PHASE-05-participant-decision.md` по шаблону
4. Claude Code реализует в ветке `feature/phase-5-participant-decision`

---
