# Краткие описания оставшихся фаз (PHASE-09 до PHASE-28)

> Этот файл содержит краткие описания фаз, для которых ещё не созданы детальные тикеты.
> При начале работы над каждой фазой Cowork подготовит полный тикет по шаблону `TEMPLATE.md`.

---

## PHASE-09: Отзыв процесса (RecallProcess)

**Приоритет:** 🟢 Средний  
**SP:** 3  
**Зависимости:** PHASE-07

**Объём:**
- `ProcessService.recall(processId, actorId)` — отзыв инициатором
- Переход `RecallProcess` (InProgress | OnRework → Recalled)
- Guards: `IsInitiator` (переиспользование), `ProcessNotFinalized` (G-P-010)
- Actions: `RevokeActiveTasks` (A-P-005), `CancelActiveStages` (A-P-006)
- Переход `TakeBackInWork` (Recalled → Draft) — взятие в работу после отзыва

**Критерии приёмки:**
- Инициатор может отозвать процесс из InProgress/OnRework → все активные задачи отозваны
- После отзыва процесс можно взять в работу → Draft
- Non-инициатор не может отозвать (guard блокирует)

---

## PHASE-10: Дополнительные согласующие (AdditionalApprover)

**Приоритет:** 🟡 Высокий  
**SP:** 5  
**Зависимости:** PHASE-05

**Объём:**
- `AdditionalApproverService.add(processId, stageId, participantId, additionalApproverId, deadline)`
- `AdditionalApproverStateMachine` (переходы `AssignAdditionalApprover`, `RecordRecommendation`, `RevokeAdditionalApprover`)
- Таблица `additional_approver` используется напрямую
- Иерархия: доп.согласующий может назначить других доп.согласующих (без ограничения глубины)
- Видимость: сквозная по всему процессу (не изолирована итерацией)
- Guards: `IsMainApproverOrAdditional` (G-AA-001), `DeadlineBeforeStageDeadline` (G-AA-002)

**Критерии приёмки:**
- Участник может назначить доп.согласующего с дедлайном
- Доп.согласующий даёт рекомендацию (не влияет на статус этапа)
- Доп.согласующий может назначить ещё одного доп.согласующего (иерархия)

---

## PHASE-12: UNIFIED — согласование маршрута ответственным

**Приоритет:** 🟡 Высокий  
**SP:** 5  
**Зависимости:** PHASE-07

**Объём:**
- `ProcessService.submitRoute(processId, actorId)` — отправка маршрута ответственному
- Переход `SubmitRoute` (Draft → PendingResponsibleApproval)
- `ResponsibleService.approveRoute/rejectRoute(processId, actorId)`
- Переходы `ApproveRoute` (PendingResponsibleApproval → InProgress), `RejectRoute` (→ Draft)
- Guards: `IsResponsible` (G-UN-001), `RouteValid` (G-UN-002)
- Actions: `SetPendingResponsibleApproval` (A-UN-001), `ActivateFirstStage` (переиспользование)

**Критерии приёмки:**
- Инициатор отправляет маршрут → процесс PendingResponsibleApproval
- Ответственный согласовывает → процесс InProgress, первый этап активирован
- Ответственный отклоняет → процесс возвращается в Draft

---

## PHASE-13: UNIFIED — финальное решение ответственного

**Приоритет:** 🟡 Высокий  
**SP:** 5  
**Зависимости:** PHASE-12, PHASE-07

**Объём:**
- Переход `AwaitFinalDecision` (InProgress → AwaitingFinalDecision) — когда все этапы пройдены
- `FinalDecisionService.recordFinalDecision(processId, decision, actorId)`
- `FinalDecisionStateMachine` (переходы `RecordFinalDecision`)
- Переходы процесса: `FinalApprove`, `FinalApproveWithComments`, `FinalReject` (AwaitingFinalDecision → Approved/ApprovedWithComments/Rejected)
- Таблица `final_decision` используется напрямую
- Guards: `IsResponsible`, `FinalDecisionPending` (G-FD-001)

**Критерии приёмки:**
- Все этапы UNIFIED пройдены → процесс AwaitingFinalDecision
- Ответственный принимает решение → процесс Approved/ApprovedWithComments/Rejected
- Финальное решение не зависит от решений участников этапов

---

## PHASE-14: Шаблоны маршрутов (Template CRUD, версионирование)

**Приоритет:** 🔴 Критический  
**SP:** 13  
**Зависимости:** PHASE-04

**Объём:**
- `TemplateService.create/update/publish/deprecate/archive`
- Таблицы `template`, `stage_template`, `slot_template` используются через репозитории
- `TemplateStateMachine` (Draft → Published → Deprecated → Archived)
- Guards: `NoActiveProcesses` (G-T-001), `AllMandatorySlotsValid` (G-T-002)
- Версионирование: новая версия = новая строка с увеличенным `version`
- Fork & Drain: старая версия → Deprecated, но активные процессы доживают на ней

**Критерии приёмки:**
- Можно создать шаблон с этапами и слотами
- Публикация шаблона проверяет валидность (обязательные слоты, сроки > 0)
- Версионирование работает: изменение опубликованного шаблона создаёт новую версию

---

## PHASE-15: Подбор шаблона и генерация маршрута (MatchService, RouteGeneratorService)

**Приоритий:** 🔴 Критический  
**SP:** 21  
**Зависимости:** PHASE-14

**Объём:**
- `MatchService.findMatchingTemplates(entitySnapshot)` — подбор по типу/подтипу/атрибутам
- `RouteGeneratorService.generateRoute(templateId, entitySnapshot)` — создание ProcessInstance со всеми stages/iterations/participants
- Резолвинг слотов: конкретный пользователь имеет приоритет, роль резолвится через адаптер (stub)
- `RouteValidatorService.validate(process)` — проверка обязательных слотов, сроков
- Snapshot-on-Start: фиксация `configVersion` на процессе

**Критерии приёмки:**
- Снимок сущности → подбор шаблона → ProcessInstance создан готовый к запуску
- Обязательные слоты заполнены (иначе ошибка)
- Сроки рассчитаны корректно

---

## PHASE-16: Автосогласование по срокам (Timer triggers)

**Приоритет:** 🟡 Высокий  
**SP:** 8  
**Зависимости:** PHASE-06

**Объём:**
- Планировщик (Spring @Scheduled) проверяет просроченные задачи
- Переход `AutoApproveParticipant` (trigger = TIMER) — для участника
- Переход `AutoApproveStage` (trigger = TIMER) — для этапа
- Guards: `DeadlineExpired` (G-PA-003), `ParticipantNotDecided` (G-PA-004)
- Actions: `RecordAutoApproval` (A-PA-004), `EvaluateAggregation` (переиспользование)

**Критерии приёмки:**
- Участник не принял решение, срок истёк → автосогласование → participant.status = AutoApproved
- Этап автосогласован → агрегация срабатывает, этап закрывается

---

## PHASE-17: Напоминания (Reminders)

**Приоритет:** 🟢 Средний  
**SP:** 3  
**Зависимости:** PHASE-16, PHASE-19

**Объём:**
- Планировщик отправляет события-напоминания участникам о просроченных задачах
- Напоминания инициатору о долгом нахождении в статусе OnRework
- `NotificationSettings` (таблица `notification_settings`) — периодичность настраивается администратором
- REST API: `GET /admin/notification-settings`, `PUT /admin/notification-settings`
- Actions: `ScheduleReminder` (A-N-001), `SendReminder` (A-N-002) — публикация события

**Критерии приёмки:**
- Задача просрочена → через заданный интервал отправлено событие-напоминание
- Администратор может настроить периодичность напоминаний

---

## PHASE-18: Автоархивация процессов (ScheduleAutoArchive)

**Приоритет:** 🟢 Средний  
**SP:** 3  
**Зависимости:** PHASE-07

**Объём:**
- Планировщик проверяет завершённые процессы (Approved/ApprovedWithComments/Rejected/Recalled)
- Переход `AutoArchive` (trigger = TIMER) — через 180 дней после `completedAt`
- Guard: `ArchiveDeadlineExpired` (G-P-011)
- Actions: `ArchiveProcess` (A-P-007), `RevokeActiveTasks` (переиспользование)
- `AdminService.restore(processId)` — восстановление из архива (только администратор)

**Критерии приёмки:**
- Процесс завершён → completedAt + 180 дней → автоматически Archived
- Администратор может восстановить процесс из архива

---

## PHASE-19: Event Publisher (Outbox pattern, RabbitMQ)

**Приоритет:** 🔴 Критический  
**SP:** 8  
**Зависимости:** все domain-фазы

**Объём:**
- `OutboxService.publish(events)` — запись в таблицу `outbox_event`
- Action: `PublishDomainEvent` (A-P-010) — добавление в `emits`
- Поллер (Spring @Scheduled) читает `outbox_event`, публикует в RabbitMQ, отмечает как `PUBLISHED`
- Retry логика для failed events

**Критерии приёмки:**
- Переход выполнен → событие записано в outbox
- Поллер опубликовал событие в RabbitMQ (Testcontainers)
- Failed события повторяются

---

## PHASE-20: Адаптеры (EntityAdapter, RoleResolverAdapter, PermissionAdapter)

**Приоритет:** 🟢 Средний  
**SP:** 8  
**Зависимости:** PHASE-15

**Объём:**
- `EntityAdapter` (реальный) — получение снимка сущности из системы-владельца (REST/gRPC клиент)
- `RoleResolverAdapter` (переход от stub к реальному) — резолвинг ролей в конкретных пользователей
- `PermissionAdapter` (stub → реальный) — проверка полномочий (КРИП)
- Конфигурация через `application.yml`: URLs внешних систем, таймауты, retry
- Guards: `UserHasPermission` (G-P-012), `RoleResolved` (G-P-013)

**Критерии приёмки:**
- Адаптер вызывает внешний endpoint → получает ответ (mock WireMock)
- Роль резолвится в список пользователей
- Полномочия проверяются через КРИП

---

## PHASE-21: REST API — группа «Процессы»

**Приоритет:** 🔴 Критический  
**SP:** 8  
**Зависимости:** PHASE-15, PHASE-07/08/09

**Объём:**
- `ProcessController` — все эндпоинты группы 12 из `07_api_contract.md`
- `POST /processes` — создание процесса из снимка сущности
- `GET /processes/{id}` — получение процесса
- `POST /processes/{id}/start` — запуск процесса
- `POST /processes/{id}/recall` — отзыв
- `POST /processes/{id}/resume` — возобновление с выбором целевого этапа
- `GET /processes` — список процессов с пагинацией/фильтрацией
- DTO-мапперы, валидация запросов, обработка ошибок

**Критерии приёмки:**
- API работает через MockMvc
- Все CRUD операции доступны
- Ошибки возвращают правильные HTTP-коды

---

## PHASE-22: REST API — группа «Решения»

**Приоритет:** 🟢 Средний  
**SP:** 3  
**Зависимости:** PHASE-05, PHASE-21

**Объём:**
- `DecisionController` — эндпоинты группы 16 из `07_api_contract.md`
- `POST .../decide` — принятие решения участником
- `GET .../decisions` — список решений по процессу/этапу
- DTO: DecisionDto, DecisionRequest

**Критерии приёмки:**
- Участник может принять решение через API
- Список решений возвращается с пагинацией

---

## PHASE-23: REST API — группа «Замечания и комментарии»

**Приоритет:** 🟢 Средний  
**SP:** 3  
**Зависимости:** PHASE-11, PHASE-21

**Объём:**
- `RemarkController`, `CommentController` — эндпоинты групп 17, 18
- `POST .../remarks` — создание замечания
- `PUT .../remarks/{remarkId}/resolve` — исправление
- `PUT .../remarks/{remarkId}/reject` — отклонение
- `POST .../comments` — создание комментария

**Критерии приёмки:**
- Полный lifecycle замечания доступен через API
- Комментарии создаются и читаются

---

## PHASE-24: REST API — группа «Шаблоны и администрирование»

**Приоритет:** 🟢 Средний  
**SP:** 5  
**Зависимости:** PHASE-14, PHASE-18, PHASE-17

**Объём:**
- `TemplateController` — CRUD шаблонов (группа 10)
- `StateMachineConfigController` — управление картами переходов (группа 23)
- `GuardActionRegistryController` — просмотр реестров (группа 24)
- `AdminController` — восстановление из архива, настройки уведомлений (группы 22, 26)

**Критерии приёмки:**
- Администратор может управлять шаблонами через API
- Конфигурация state machine доступна для чтения/изменения
- Настройки уведомлений изменяются через API

---

## PHASE-25: REST API — агрегированные эндпоинты для Front

**Приоритет:** 🟢 Средний  
**SP:** 5  
**Зависимости:** PHASE-21-24

**Объём:**
- `ApprovalViewController` — готовые модели для UI
- `GET /entities/{entityId}/approval-view` — процесс + этапы + участники + решения + замечания в одном запросе
- `GET /tasks/my` — задачи текущего пользователя
- `GET /processes/my` — процессы текущего пользователя
- DTO: ApprovalViewDto, TaskDto (агрегированные)

**Критерии приёмки:**
- Один запрос возвращает все данные для отрисовки UI
- Производительность: <500ms для процесса с 10 этапами

---

## PHASE-26: Ревизии документа (процессы-ревизии)

**Приоритет:** ⚪ Низкий  
**SP:** 3  
**Зависимости:** PHASE-08, PHASE-15

**Объём:**
- `ProcessService.createRevision(originalProcessId, revisionLabel, interruptOriginal)`
- Создание нового процесса, связанного через `documentAggregateId`
- Guard: `ProcessIterationEnabled` (G-P-014) — проверяет флаг шаблона
- Actions: `InterruptOriginalProcess` (A-P-011)
- Событие `approval.process.revision-created`

**Критерии приёмки:**
- Процесс OnRework → инициатор создаёт ревизию → новый процесс создан
- Старый процесс прерван (опционально)
- Оба процесса связаны через `documentAggregateId`

---

## PHASE-27: Sequential execution order (назначение следующего участника)

**Приоритет:** ⚪ Низкий  
**SP:** 3  
**Зависимости:** PHASE-05

**Объём:**
- Action: `AssignNextParticipantTask` (A-S-009) — при `executionOrder = Sequential` после решения участника
- Guard: `PreviousParticipantDecided` (G-S-007)
- Переход `AssignNextTask` (ParticipantStateMachine) — Pending → Assigned для следующего по orderIdx

**Критерии приёмки:**
- Этап Sequential → первый участник решил → второй получил задачу (Pending → Assigned)
- Второй участник не может решить, пока не решил первый (guard блокирует)

---

## PHASE-28: Дополнительные guards и actions (расширение реестра)

**Приоритет:** ⚪ Низкий  
**SP:** 5  
**Зависимости:** все domain-фазы

**Объём:**
- Реализация оставшихся guards/actions из `05_guards_actions_registry.md`
- Примеры: `UserInOrganization` (G-P-015), `EntityAttributeMatch` (G-P-016), `LogAuditDetails` (A-P-012)
- Покрытие редких граничных случаев
- Юнит-тесты на каждый guard/action

**Критерии приёмки:**
- Все guards/actions из реестра реализованы
- 100% покрытие тестами

---

## Сводка по не созданным детальным тикетам

| Фаза | Название | Приоритет | SP | Статус тикета |
|------|----------|-----------|-----|---------------|
| PHASE-09 | Отзыв процесса | 🟢 Средний | 3 | Краткое описание |
| PHASE-10 | Дополнительные согласующие | 🟡 Высокий | 5 | Краткое описание |
| PHASE-12 | UNIFIED — маршрут | 🟡 Высокий | 5 | Краткое описание |
| PHASE-13 | UNIFIED — финал | 🟡 Высокий | 5 | Краткое описание |
| PHASE-14 | Шаблоны | 🔴 Критический | 13 | Краткое описание |
| PHASE-15 | Генерация маршрута | 🔴 Критический | 21 | Краткое описание |
| PHASE-16 | Автосогласование | 🟡 Высокий | 8 | Краткое описание |
| PHASE-17 | Напоминания | 🟢 Средний | 3 | Краткое описание |
| PHASE-18 | Автоархивация | 🟢 Средний | 3 | Краткое описание |
| PHASE-19 | Event Publisher | 🔴 Критический | 8 | Краткое описание |
| PHASE-20 | Адаптеры | 🟢 Средний | 8 | Краткое описание |
| PHASE-21 | REST API — Процессы | 🔴 Критический | 8 | Краткое описание |
| PHASE-22 | REST API — Решения | 🟢 Средний | 3 | Краткое описание |
| PHASE-23 | REST API — Замечания | 🟢 Средний | 3 | Краткое описание |
| PHASE-24 | REST API — Администрирование | 🟢 Средний | 5 | Краткое описание |
| PHASE-25 | REST API — Агрегированные | 🟢 Средний | 5 | Краткое описание |
| PHASE-26 | Ревизии | ⚪ Низкий | 3 | Краткое описание |
| PHASE-27 | Sequential execution | ⚪ Низкий | 3 | Краткое описание |
| PHASE-28 | Дополнительные guards/actions | ⚪ Низкий | 5 | Краткое описание |

**Всего:** 19 фаз, 112 SP

---

## Когда создавать детальные тикеты

Детальные тикеты по шаблону `TEMPLATE.md` создаются **Cowork** непосредственно перед началом реализации фазы. Это гарантирует, что:
- Тикет учитывает фактическое состояние кода после предыдущих фаз
- Нет расхождений между планом и реализацией
- Открытые вопросы из предыдущих фаз учтены в новом тикете

**Процесс:**
1. Завершена фаза N → Claude Code открывает PR
2. Cowork проверяет PR, мержит
3. Cowork анализирует что изменилось vs спека
4. Cowork готовит детальный тикет для фазы N+1
5. Пользователь размещает тикет в репозитории
6. Claude Code реализует фазу N+1

---
