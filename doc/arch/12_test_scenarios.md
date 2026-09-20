# Тестовые сценарии

**Версия:** 2.1  
**Назначение:** полный набор тестовых сценариев для проверки бизнес-логики модуля «Согласование»

---

## Оглавление

1. Назначение и обоснование
2. Ключевые понятия
3. Формат сценариев
4. Группа 1: Создание и запуск процесса
5. Группа 2: Согласование этапа (STANDARD)
6. Группа 3: Отклонение и возврат на этап
7. Группа 4: Итерации этапа
8. Группа 5: Дополнительные согласующие
9. Группа 6: Замечания и комментарии
10. Группа 7: Отзыв и возобновление
11. Группа 8: Единый процесс (UNIFIED)
12. Группа 9: Финальное решение
13. Группа 10: Ревизии документа
14. Группа 11: Архивация
15. Группа 12: Агрегация для Front
16. Группа 13: Конфигурация карт переходов
17. Группа 14: Интеграционные сценарии
18. Сводная таблица

---

## 1. Назначение и обоснование

### 1.1 Что описывает документ

**Тестовые сценарии** — набор формализованных сценариев для проверки бизнес-логики модуля. Используются для:
- приёмочного тестирования;
- регрессионного тестирования;
- обучения новых разработчиков;
- документирования ожидаемого поведения.

### 1.2 Зачем нужны

**Проблема.** Без формализованных сценариев:
- Разработчики не знают, как система должна себя вести.
- Регрессии не отлавливаются.
- Приёмочное тестирование превращается в ручной хаос.

**Решение.** Формализовать сценарии в формате BDD (Given-When-Then).

### 1.3 Формат

**BDD** (Behaviour-Driven Development) — подход, при котором сценарии описываются в терминах:

- **Given** (Дано) — начальное состояние.
- **When** (Когда) — действие.
- **Then** (Тогда) — ожидаемый результат.

---

## 2. Ключевые понятия

### 2.1 Сценарий

**Сценарий** — один конкретный случай с чёткими предусловиями и ожидаемым результатом.

### 2.2 Тестовые данные

**Тестовые данные** — идентификаторы и значения, используемые в сценариях.

**Примеры:**
- `user-1`, `user-2` — пользователи.
- `org-1`, `org-2` — организации.
- `template-1` — шаблон.
- `process-1` — процесс.

### 2.3 Моки и стабы

**Моки** — заглушки адаптеров для изоляции.

**Пустые реализации** используются в тестах по умолчанию.

---

## 3. Формат сценариев

### 3.1 Структура

```gherkin
Feature: Название фичи

  Scenario: Название сценария
    Given <начальное состояние>
    And <дополнительное условие>
    When <действие>
    Then <ожидаемый результат>
    And <дополнительный результат>
```

### 3.2 Правила

- Один сценарий — один результат.
- Названия — на русском.
- Тестовые данные — конкретные (без абстракций).
- Сценарии независимы друг от друга.

---

## 4. Группа 1: Создание и запуск процесса

### Сценарий 1.1: Успешное создание процесса

```gherkin
Scenario: Инициатор создаёт процесс на основе шаблона
  Given существует шаблон template-1 с 3 этапами
  And шаблон опубликован (статус PUBLISHED)
  And пользователь user-1 является инициатором
  When user-1 отправляет запрос POST /processes
  Then создаётся процесс в статусе DRAFT
  And в процессе 3 этапа
  And processType = STANDARD
  And инициатор = user-1
```

### Сценарий 1.2: Запуск процесса с заполненными слотами

```gherkin
Scenario: Инициатор запускает процесс
  Given процесс process-1 в статусе DRAFT
  And все обязательные слоты заполнены
  And у всех этапов срок > 0
  When user-1 отправляет POST /processes/process-1/start
  Then процесс переходит в статус IN_PROGRESS
  And создаются задачи участникам первого этапа
  And публикуется событие approval.process.started
  And публикуются события approval.task.assigned для каждого участника
```

### Сценарий 1.3: Ошибка запуска — незаполненные слоты

```gherkin
Scenario: Запуск блокируется, если не заполнены обязательные слоты
  Given процесс process-1 в статусе DRAFT
  And на этапе 1 есть обязательный слот без userId
  When user-1 отправляет POST /processes/process-1/start
  Then возвращается ошибка MANDATORY_SLOTS_NOT_FILLED
  And процесс остаётся в статусе DRAFT
```

### Сценарий 1.4: Ошибка запуска — некорректный срок

```gherkin
Scenario: Запуск блокируется при duration <= 0
  Given процесс process-1 в статусе DRAFT
  And у этапа 2 duration = 0
  When user-1 отправляет POST /processes/process-1/start
  Then возвращается ошибка DURATION_INVALID
  And процесс остаётся в статусе DRAFT
```

### Сценарий 1.5: Ошибка запуска — не инициатор

```gherkin
Scenario: Запуск доступен только инициатору
  Given процесс process-1 создан user-1
  When user-2 отправляет POST /processes/process-1/start
  Then возвращается ошибка FORBIDDEN
```

---

## 5. Группа 2: Согласование этапа (STANDARD)

### Сценарий 2.1: Режим AND — все согласовали

```gherkin
Scenario: Этап закрывается успешно при режиме AND
  Given этап 1 с режимом AND
  And участники: user-1, user-2
  And процесс в статусе IN_PROGRESS
  When user-1 принимает решение APPROVE
  And user-2 принимает решение APPROVE
  Then этап 1 переходит в статус APPROVED
  And публикуется событие approval.stage.completed
  And активируется этап 2
```

### Сценарий 2.2: Режим AND — один отклонил, ждём всех

```gherkin
Scenario: AND — этап уходит на доработку после решения всех
  Given этап 1 с режимом AND
  And участники: user-1, user-2, user-3
  When user-1 принимает решение APPROVE
  And user-2 принимает решение REJECT
  Then этап 1 не закрыт
  And ожидается решение user-3
  When user-3 принимает решение APPROVE
  Then этап 1 переходит в статус ON_REWORK
  And публикуется событие approval.process.rework-required
```

### Сценарий 2.3: Режим ANY_APPROVE — первый согласовавший

```gherkin
Scenario: ANY_APPROVE — первый approve закрывает этап
  Given этап 1 с режимом ANY_APPROVE
  And участники: user-1, user-2
  When user-1 принимает решение APPROVE
  Then этап 1 переходит в статус APPROVED
  When user-2 принимает решение REJECT
  Then решение user-2 игнорируется
```

### Сценарий 2.4: Режим ANY_REJECT — первый отклонивший

```gherkin
Scenario: ANY_REJECT — первый reject отправляет на доработку
  Given этап 1 с режимом ANY_REJECT
  And участники: user-1, user-2
  When user-1 принимает решение APPROVE
  Then ожидается решение user-2
  When user-2 принимает решение REJECT
  Then этап 1 переходит в статус ON_REWORK
  And ожидание остальных участников прекращается
```

### Сценарий 2.5: Режим ANY_DECISION — первое решение

```gherkin
Scenario: ANY_DECISION — первое решение определяет итог
  Given этап 1 с режимом ANY_DECISION
  And участники: user-1, user-2
  When user-2 принимает решение REJECT
  Then этап 1 переходит в статус ON_REWORK
  And решение user-1 не учитывается
```

### Сценарий 2.6: Режим FIRST_REJECT_FAIL_FAST

```gherkin
Scenario: FIRST_REJECT_FAIL_FAST — первый отказ прерывает
  Given этап 1 с режимом FIRST_REJECT_FAIL_FAST
  And участники: user-1, user-2, user-3
  When user-1 принимает решение APPROVE
  And user-2 принимает решение REJECT
  Then этап 1 переходит в статус ON_REWORK
  And user-3 не получает запрос на решение
```

### Сценарий 2.7: Автосогласование по сроку

```gherkin
Scenario: Автосогласование при истечении срока
  Given этап 1 с участниками user-1, user-2
  And срок этапа истёк
  And user-1 не принял решение
  When срабатывает таймер DueDateExpired
  Then user-1 получает статус AUTO_APPROVED
  And публикуется событие approval.decision.recorded (auto=true)
  And публикуется событие approval.stage.auto-approved
```

### Сценарий 2.8: Sequential — последовательный порядок работы участников (ADR-022)

```gherkin
Scenario: Sequential — участники получают задачи по очереди
  Given этап 1 с executionOrder = SEQUENTIAL
  And участники: user-1 (orderIdx=1), user-2 (orderIdx=2), user-3 (orderIdx=3)
  And только user-1 находится в статусе ACTIVE, остальные — PENDING
  When user-1 принимает решение APPROVE
  Then guard CanAssignTask (G-PA-007) разрешает переход участника user-2
  And действие AssignNextParticipantTask (A-S-009) переводит user-2 из PENDING в ACTIVE
  And публикуется событие approval.task.assigned для user-2 (orderIdx=2)
  And user-3 остаётся в статусе PENDING
  When user-2 принимает решение REJECT
  Then этап 1 переходит в статус ON_REWORK (согласно decisionMode этапа)
  And user-3 не получает задачу — очередь Sequential прерывается
```

---

## 6. Группа 3: Отклонение и возврат на этап

### Сценарий 3.1: Возврат на текущий этап

```gherkin
Scenario: Возврат на текущий этап — только новая итерация этапа
  Given этап 1 APPROVED
  And этап 2 APPROVED
  And этап 3 отклонён (ON_REWORK)
  And allowedReturnStages этапа 3 = [1, 2, 3]
  When инициатор выбирает targetStageId = этап 3
  And отправляет POST /processes/process-1/resume
  Then этап 3 создаёт Iteration 2
  And этапы 1 и 2 остаются на Iter 1
  And процесс переходит в IN_PROGRESS
```

### Сценарий 3.2: Возврат на этап 1

```gherkin
Scenario: Возврат на этап 1 — все этапы получают новую итерацию
  Given этап 1 Iter 1 APPROVED
  And этап 2 Iter 1 APPROVED
  And этап 3 Iter 1 ON_REWORK
  And allowedReturnStages этапа 3 = [1, 2, 3]
  When инициатор выбирает targetStageId = этап 1
  And отправляет POST /processes/process-1/resume
  Then этап 1 создаёт Iteration 2
  And этап 2 сбрасывается в PENDING
  And этап 3 сбрасывается в PENDING
  And все этапы получат Iteration 2 при активации
```

### Сценарий 3.3: Возврат на этап 2

```gherkin
Scenario: Возврат на этап 2 — этап 1 остаётся
  Given этап 1 Iter 1 APPROVED
  And этап 2 Iter 1 APPROVED
  And этап 3 Iter 1 ON_REWORK
  When инициатор выбирает targetStageId = этап 2
  Then этап 1 остаётся Iter 1 APPROVED
  And этап 2 создаёт Iteration 2
  And этап 3 сбрасывается в PENDING
```

### Сценарий 3.4: Некорректная цель возврата

```gherkin
Scenario: Возврат на этап вне allowedReturnStages блокируется
  Given allowedReturnStages этапа 3 = [2, 3]
  When инициатор выбирает targetStageId = этап 1
  Then возвращается ошибка INVALID_RETURN_TARGET
```

### Сценарий 3.5: Возврат при наличии необработанных замечаний

```gherkin
Scenario: Возврат блокируется, если есть Active или InProgress замечания
  Given этап 3 ON_REWORK
  And 2 замечания в статусе ACTIVE
  When инициатор отправляет POST /processes/process-1/resume
  Then возвращается ошибка ACTIVE_REMARKS_PRESENT
```

### Сценарий 3.6: Статус «Отклонён» у этапа

```gherkin
Scenario: Этап получает статус REJECTED при возврате на более ранний этап
  Given этап 2 ACTIVE
  And этап 3 ACTIVE
  When инициатор возвращает на этап 1
  Then этап 2 переходит в статус REJECTED
  And этап 3 переходит в статус REJECTED
  And публикуется approval.stage.rejected для обоих
```

### Сценарий 3.7: Программная инициация StageRejectedByReturn (ADR-025)

```gherkin
Scenario: Переход StageRejectedByReturn инициируется действием процесса, а не самостоятельно
  Given процесс переходит по ResumeProcess с targetStageId = этап 1
  When действие A-RT-002 RejectStagesFrom(target=этап 1) выполняется на уровне процесса
  Then для каждого промежуточного этапа (2, 3) вызывается переход StageRejectedByReturn
  And guard G-S-007 ReturnToPriorStage получает returnTargetStage = этап 1 из контекста, переданного A-RT-002
  And guard возвращает true, так как returnTargetStage.orderIdx < currentStage.orderIdx
  And этапы 2 и 3 переходят в статус REJECTED
```

---

## 7. Группа 4: Итерации этапа

### Сценарий 4.1: Новая итерация при активации

```gherkin
Scenario: При повторной активации этапа создаётся новая итерация
  Given этап 1 имеет Iteration 1
  And этап 1 был SUPERSEDED
  When этап 1 активируется снова
  Then создаётся Iteration 2
  And Iteration 2 получает статус ACTIVE
```

### Сценарий 4.2: Нормальный переход на следующий этап

```gherkin
Scenario: При первом переходе на этап создаётся Iteration 1
  Given этап 2 не активировался ранее
  When этап 1 согласован
  Then этап 2 активируется
  And этап 2 создаёт Iteration 1
```

### Сценарий 4.3: Вычисляемая итерация процесса

```gherkin
Scenario: Итерация процесса вычисляется как max(stageIteration)
  Given этап 1: Iteration 2
  And этап 2: Iteration 2
  And этап 3: Iteration 1
  When запрашивается approval-view
  Then processIteration = 2
```

---

## 8. Группа 5: Дополнительные согласующие

### Сценарий 5.1: Добавление доп. согласующего основным

```gherkin
Scenario: Основной согласующий добавляет доп. согласующего
  Given этап 1 ACTIVE
  And user-1 — участник
  When user-1 отправляет POST .../additional-approvers
  And указывает userId = user-10
  Then создаётся AdditionalApprover
  And assignedBy = PARTICIPANT
  And level = 1
  And публикуется approval.task.assigned
```

### Сценарий 5.2: Доп. согласующий добавляет другого доп.

```gherkin
Scenario: Доп. согласующий добавляет дочернего
  Given AdditionalApprover aa-1 создан user-1
  And user-10 — этот доп. согласующий
  When user-10 отправляет POST .../aa-1/children
  And указывает userId = user-11
  Then создаётся AdditionalApprover aa-2
  And aa-2.parentAdditionalApproverId = aa-1
  And aa-2.level = 2
  And aa-2.assignedBy = ADDITIONAL_APPROVER
```

### Сценарий 5.3: Видимость — доп. видит основного

```gherkin
Scenario: Доп. согласующий видит основного
  Given user-10 — доп. согласующий
  When user-10 запрашивает approval-view
  Then в ответе присутствуют основные согласующие этапа
```

### Сценарий 5.4: Видимость — доп. видит других доп. глобально

```gherkin
Scenario: Доп. согласующий видит всех доп. согласующих процесса
  Given этап 1 Iteration 1 — доп. aa-1
  And этап 1 Iteration 2 — доп. aa-2
  When user-10 (доп.) запрашивает approval-view
  Then в ответе присутствуют aa-1 и aa-2
```

### Сценарий 5.5: Рекомендация доп. согласующего не унифицирована с Decision (ADR-016)

```gherkin
Scenario: Рекомендация доп. согласующего носит информационный характер и не пишется в decision
  Given этап 1 ACTIVE, режим AND
  And основной участник user-1 ещё не принял решение
  And доп. согласующий user-10 (aa-1) даёт рекомендацию REJECT через POST .../additional-approvers/aa-1/decision
  Then рекомендация записывается в additional_approver.recommendation
  And запись в таблице decision НЕ создаётся
  And evaluateAggregation(этап 1) не учитывает рекомендацию aa-1
  When user-1 принимает решение APPROVE
  Then этап 1 переходит в статус APPROVED несмотря на рекомендацию REJECT от aa-1
  And рекомендация aa-1 остаётся видна user-1 как информационная (только для его собственного решения)
```

### Сценарий 5.6: Поднятие замечаний до основного

```gherkin
Scenario: Замечание доп. уровня 2 поднимается до основного
  Given aa-2 (level=2) создал замечание
  When основной согласующий запрашивает замечания
  Then замечание aa-2 присутствует в списке
  And основной может его активировать или пометить NotRequired
```

### Сценарий 5.7: Клонирование при новой итерации

```gherkin
Scenario: Доп. от шаблона/инициатора клонируется; от участника — нет
  Given aa-1 создан из TEMPLATE
  And aa-2 создан инициатором
  And aa-3 создан основным согласующим
  And aa-4 создан доп. согласующим
  When создаётся новая итерация этапа
  Then aa-1 клонируется
  And aa-2 клонируется
  And aa-3 не клонируется
  And aa-4 не клонируется
```

### Сценарий 5.8: Клонирование родителя с детьми

```gherkin
Scenario: При клонировании родителя клонируются дети
  Given aa-1 создан из TEMPLATE
  And aa-1.1 создан aa-1
  When создаётся новая итерация
  Then aa-1 клонируется
  And aa-1.1 клонируется с новым родителем
```

---

## 9. Группа 6: Замечания и комментарии

### Сценарий 6.1: Основной создаёт замечание

```gherkin
Scenario: Замечание основного сразу Active
  Given user-1 — основной согласующий
  When user-1 отправляет POST /remarks
  Then замечание создано
  And status = ACTIVE
  And authorRole = APPROVER
  And публикуется approval.remark.created
```

### Сценарий 6.2: Доп. создаёт замечание

```gherkin
Scenario: Замечание доп. — Proposed
  Given user-10 — доп. согласующий
  When user-10 отправляет POST /remarks
  Then замечание создано
  And status = PROPOSED
  And authorRole = ADDITIONAL_APPROVER
```

### Сценарий 6.3: Активация замечания доп.

```gherkin
Scenario: Основной активирует замечание доп.
  Given замечание remark-1 в статусе PROPOSED
  And user-1 — основной согласующий
  And user-1 ещё не принял решение
  When user-1 отправляет POST /remarks/remark-1/activate
  Then remark-1.status = ACTIVE
  And публикуется approval.remark.activated
```

### Сценарий 6.4: Активация после решения запрещена

```gherkin
Scenario: Активация Proposed после своего решения запрещена
  Given remark-1 в статусе PROPOSED
  And user-1 (основной) принял решение APPROVE
  When user-1 пытается активировать remark-1
  Then возвращается ошибка BUSINESS_ERROR
```

### Сценарий 6.5: Инициатор исправляет замечание

```gherkin
Scenario: Инициатор помечает замечание Resolved
  Given remark-1 в статусе ACTIVE
  When инициатор отправляет POST /remarks/remark-1/resolve
  Then remark-1.status = RESOLVED
  And публикуется approval.remark.resolved
```

### Сценарий 6.6: Инициатор отклоняет замечание

```gherkin
Scenario: Инициатор отклоняет замечание с обоснованием
  Given remark-1 в статусе ACTIVE
  When инициатор отправляет POST /remarks/remark-1/reject
  And указывает reason = "Не относится к документу"
  Then remark-1.status = REJECTED
  And remark-1.rejectionReason заполнен
```

### Сценарий 6.7: Закрытие замечаний при Approve

```gherkin
Scenario: Все Active замечания автора закрываются при Approve
  Given user-1 создал 3 замечания в статусе ACTIVE
  And на этапе только user-1
  When user-1 принимает решение APPROVE
  Then все 3 замечания переходят в статус CLOSED
```

### Сценарий 6.8: AutoProcessed при закрытии этапа

```gherkin
Scenario: Необработанные Proposed авто-обрабатываются
  Given замечание remark-1 в статусе PROPOSED
  And этап закрывается (согласован без решения основного по remark-1)
  Then remark-1.status = AUTO_PROCESSED
  And публикуется approval.remark.auto-processed
```

### Сценарий 6.9: Валидация комментария при ApproveWithComments

```gherkin
Scenario: ApproveWithComments требует комментарий
  Given user-1 — участник
  When user-1 принимает решение APPROVE_WITH_COMMENTS без комментария
  Then возвращается ошибка COMMENT_REQUIRED
  And решение не фиксируется
```

### Сценарий 6.10: Валидация при Reject

```gherkin
Scenario: Reject требует комментарий или замечание
  Given user-1 — участник
  When user-1 принимает решение REJECT без комментария и замечания
  Then возвращается ошибка COMMENT_REQUIRED
```

---

## 10. Группа 7: Отзыв и возобновление

### Сценарий 7.1: Отзыв процесса инициатором

```gherkin
Scenario: Инициатор отзывает процесс
  Given процесс process-1 в статусе IN_PROGRESS
  When user-1 отправляет POST /processes/process-1/recall
  Then process-1.status = RECALLED
  And активные задачи отменяются
  And публикуется approval.process.recalled
```

### Сценарий 7.2: Отзыв после OnRework

```gherkin
Scenario: Инициатор отзывает процесс из OnRework
  Given процесс process-1 в статусе ON_REWORK
  When user-1 отправляет POST /processes/process-1/recall
  Then process-1.status = RECALLED
```

### Сценарий 7.3: Взятие отозванного в работу

```gherkin
Scenario: Инициатор берёт отозванный процесс в работу
  Given процесс process-1 в статусе RECALLED
  When user-1 отправляет POST /processes/process-1/rework
  Then process-1.status = DRAFT
  And статусы этапов сбрасываются в PENDING
```

### Сценарий 7.4: Возобновление процесса

```gherkin
Scenario: Возобновление STANDARD процесса
  Given процесс process-1 в статусе ON_REWORK
  And все замечания обработаны
  When инициатор выбирает targetStageId
  And отправляет POST /processes/process-1/resume
  Then process-1.status = IN_PROGRESS
  And создаются новые итерации для этапов
```

---

## 11. Группа 8: Единый процесс (UNIFIED)

### Сценарий 8.1: Согласование маршрута ответственным

```gherkin
Scenario: Ответственный согласовывает маршрут
  Given процесс process-1 в статусе PENDING_RESPONSIBLE_APPROVAL
  And user-responsible — ответственный
  When user-responsible отправляет POST /processes/process-1/approve-route
  Then process-1.status = IN_PROGRESS
  And активируется этап 1
```

### Сценарий 8.2: Отклонение маршрута

```gherkin
Scenario: Ответственный отклоняет маршрут
  Given процесс process-1 в статусе PENDING_RESPONSIBLE_APPROVAL
  When user-responsible отправляет POST /processes/process-1/reject-route
  And указывает comment
  Then process-1.status = DRAFT
  And инициатор получает уведомление
```

### Сценарий 8.3: Прохождение этапов без влияния решений

```gherkin
Scenario: Отклонение участника не останавливает процесс
  Given процесс process-1 UNIFIED
  And этап 1 ACTIVE
  And участники user-1, user-2
  When user-1 принимает решение REJECT
  And user-2 принимает решение APPROVE
  Then этап 1 переходит в статус COMPLETED
  And процесс продолжается на этап 2
```

### Сценарий 8.4: Ответственный как участник

```gherkin
Scenario: Ответственный может быть участником этапа
  Given user-responsible — ответственный
  And user-responsible — участник этапа 1
  When user-responsible принимает решение APPROVE
  Then решение фиксируется как обычное
  And финальное решение не зависит от этого голоса
```

### Сценарий 8.5: Финальное решение

```gherkin
Scenario: Ответственный принимает финальное решение
  Given процесс process-1 в статусе AWAITING_FINAL_DECISION
  When user-responsible отправляет POST /processes/process-1/final-decision
  And указывает result = APPROVE
  Then process-1.status = APPROVED
  And финальное решение immutable
  And публикуется approval.unified.final-decision.recorded
```

### Сценарий 8.6: Финальное решение — отклонение

```gherkin
Scenario: Ответственный отклоняет несмотря на согласие участников
  Given процесс process-1 в статусе AWAITING_FINAL_DECISION
  And все этапы COMPLETED
  When user-responsible отправляет final-decision = REJECT
  Then process-1.status = REJECTED
```

### Сценарий 8.7: Срок финального решения не устанавливается

```gherkin
Scenario: Финальное решение без срока
  Given процесс process-1 в статусе AWAITING_FINAL_DECISION
  When истекает любой срок
  Then процесс НЕ переходит в REJECTED автоматически
  And ждёт решения ответственного
```

---

## 12. Группа 9: Финальное решение

### Сценарий 9.1: Валидация роли

```gherkin
Scenario: Финальное решение доступно только ответственному
  Given процесс process-1 в статусе AWAITING_FINAL_DECISION
  When user-1 (не ответственный) отправляет final-decision
  Then возвращается ошибка FORBIDDEN
```

### Сценарий 9.2: Валидация состояния

```gherkin
Scenario: Финальное решение только из AwaitingFinalDecision
  Given процесс process-1 в статусе IN_PROGRESS
  When user-responsible отправляет final-decision
  Then возвращается ошибка BUSINESS_ERROR
```

### Сценарий 9.3: Immutable

```gherkin
Scenario: Финальное решение нельзя изменить
  Given процесс process-1 APPROVED
  When user-responsible пытается изменить финальное решение
  Then возвращается ошибка BUSINESS_ERROR
```

---

## 13. Группа 10: Ревизии документа

### Сценарий 10.1: Создание ревизии

```gherkin
Scenario: Инициатор создаёт ревизию документа
  Given процесс process-1 в статусе APPROVED
  And шаблон template-1 имеет processIterationEnabled = true
  When инициатор отправляет POST /processes/revision
  And указывает revisionLabel = "Изм. 2"
  And interruptPrevious = true
  Then создаётся новый процесс process-2
  And process-2.documentAggregateId = process-1.documentAggregateId
  And process-2.parentProcessId = process-1.id
  And process-2.revisionLabel = "Изм. 2"
  And process-1.status = RECALLED
  And публикуется approval.process.revision-created
```

### Сценарий 10.2: Ревизия без прерывания

```gherkin
Scenario: Ревизия без прерывания предыдущего процесса
  Given процесс process-1 в статусе IN_PROGRESS
  When инициатор отправляет POST /processes/revision
  И указывает interruptPrevious = false
  Then создаётся process-2
  And process-1 продолжает работу
  And оба связаны через documentAggregateId
```

### Сценарий 10.3: Дублирование метки

```gherkin
Scenario: Дублирование revisionLabel запрещено
  Given в агрегате agg-1 уже есть ревизия "Изм. 2"
  When инициатор создаёт ревизию с revisionLabel = "Изм. 2"
  Then возвращается ошибка REVISION_LABEL_DUPLICATE
```

### Сценарий 10.4: Ревизии запрещены шаблоном

```gherkin
Scenario: Ревизия запрещена, если processIterationEnabled = false
  Given шаблон template-2 имеет processIterationEnabled = false
  When инициатор создаёт ревизию по шаблону template-2
  Then возвращается ошибка REVISION_NOT_ALLOWED
```

### Сценарий 10.5: Пустая метка ревизии

```gherkin
Scenario: Пустая метка ревизии запрещена
  Given валидные данные
  When revisionLabel = ""
  Then возвращается ошибка REVISION_LABEL_EMPTY
```

### Сценарий 10.6: Список ревизий

```gherkin
Scenario: Список ревизий агрегата
  Given агрегат agg-1 содержит 3 ревизии
  When запрашивается GET /entities/aggregate/agg-1/revisions
  Then возвращается список из 3 ревизий
  And у каждой указаны revisionLabel, processId, status, isActive
```

---

## 14. Группа 11: Архивация

### Сценарий 11.1: Автоматическая архивация

```gherkin
Scenario: Автоархивация через 180 дней
  Given процесс process-1 в статусе APPROVED
  And с момента завершения прошло 180 дней
  When срабатывает планировщик
  Then process-1.status = ARCHIVED
  And process-1.archivedAt заполнен
  And публикуется approval.process.auto-archived
```

### Сценарий 11.2: Архивация только финальных процессов

```gherkin
Scenario: Процесс в IN_PROGRESS не архивируется
  Given процесс process-1 в статусе IN_PROGRESS
  And с момента старта прошло 200 дней
  When срабатывает планировщик
  Then process-1 не архивируется
```

### Сценарий 11.3: Восстановление из архива

```gherkin
Scenario: Администратор восстанавливает процесс
  Given процесс process-1 в статусе ARCHIVED
  And user-admin — администратор
  When user-admin отправляет POST /processes/process-1/restore
  Then process-1.status = APPROVED (previousStatus)
  And process-1.autoArchiveScheduledAt = now + 180 дней
  And публикуется approval.process.restored
```

### Сценарий 11.4: Восстановление только админом

```gherkin
Scenario: Восстановление доступно только администратору
  Given процесс process-1 в статусе ARCHIVED
  When user-1 (не админ) отправляет restore
  Then возвращается ошибка FORBIDDEN
```

### Сценарий 11.5: Повторная архивация после восстановления

```gherkin
Scenario: После восстановления процесс снова архивируется через 180 дней
  Given процесс process-1 восстановлен 01.04.2027
  When прошло 180 дней
  Then process-1 снова архивируется
```

---

## 15. Группа 12: Агрегация для Front

### Сценарий 12.1: ApprovalView по сущности

```gherkin
Scenario: Агрегированное представление по entityRef
  Given 3 процесса связаны с одним entityId
  And один из них активный
  When запрашивается GET /entities/approval-view
  Then возвращается активный процесс
  And включены processIterations, stages, iterations, participants, actions
```

### Сценарий 12.2: Активного нет — последний завершённый

```gherkin
Scenario: Возврат последнего завершённого процесса
  Given для entityRef нет активных процессов
  And есть завершённый процесс
  When запрашивается approval-view
  Then возвращается последний завершённый
```

### Сценарий 12.3: Ничего нет — 404

```gherkin
Scenario: Нет процессов для entityRef
  Given для entityRef нет процессов
  When запрашивается approval-view
  Then возвращается 404 NOT_FOUND
```

### Сценарий 12.4: Параметр full=false

```gherkin
Scenario: Последняя итерация развёрнута, предыдущие свёрнуты
  Given процесс с 2 итерациями
  When запрашивается approval-view без full
  Then итерация 2 развёрнута
  And итерация 1 содержит только summary
```

### Сценарий 12.5: Параметр full=true

```gherkin
Scenario: Все итерации развёрнуты при full=true
  Given процесс с 2 итерациями
  When запрашивается approval-view с full=true
  Then обе итерации развёрнуты с participants
```

### Сценарий 12.6: Секция actions для инициатора

```gherkin
Scenario: Действия для роли INITIATOR
  Given user-1 — инициатор процесса в IN_PROGRESS
  When запрашивается approval-view
  Then canRecall = true
  And canDecide = false
  And canResolveRemark = true (если есть Active замечания)
```

### Сценарий 12.7: Секция actions для согласующего

```gherkin
Scenario: Действия для роли APPROVER
  Given user-1 — активный участник этапа
  When запрашивается approval-view
  Then canDecide = true
  And canAddAdditionalApprover = true
  And canRecall = false
```

### Сценарий 12.8: Замечания и комментарии — единый список

```gherkin
Scenario: Замечания и комментарии в одном запросе
  Given процесс с 5 замечаниями и 3 комментариями
  When запрашивается GET /entities/approval-remarks
  Then возвращается 8 записей
  And каждая содержит type, status, author, stageId, participantId, createdAt
```

### Сценарий 12.9: Параметр type в approval-remarks

```gherkin
Scenario: Фильтр по типу
  Given процесс с замечаниями и комментариями
  When запрашивается с type = REMARK
  Then возвращаются только замечания
```

### Сценарий 12.10: Секция actions — отдельный эндпоинт

```gherkin
Scenario: Точечный запрос действий
  Given процесс в IN_PROGRESS
  When запрашивается GET /entities/approval-actions
  Then возвращаются только флаги actions
```

### Сценарий 12.11: Кэширование approval-view

```gherkin
Scenario: Повторный запрос из кэша
  Given approval-view был запрошен
  When повторно запрашивается тот же view
  Then ответ приходит из Redis
  And response time < 100ms
```

---

## 16. Группа 13: Конфигурация карт переходов

### Сценарий 13.1: Публикация конфига

```gherkin
Scenario: Администратор публикует конфиг
  Given конфиг в статусе DRAFT
  And все guards/actions зарегистрированы
  And ровно одно initial состояние
  When user-admin публикует конфиг
  Then конфиг переходит в PUBLISHED
  And публикуется approval.config.published
```

### Сценарий 13.2: Валидация конфига

```gherkin
Scenario: Публикация блокируется при отсутствии initial
  Given конфиг без initial состояния
  When user-admin публикует конфиг
  Then возвращается ошибка VALIDATION_ERROR
```

### Сценарий 13.3: Депрекация конфига

```gherkin
Scenario: Депрекация при отсутствии активных процессов
  Given конфиг PUBLISHED
  And activeProcessCount = 0
  When user-admin отправляет deprecate
  Then конфиг переходит в DEPRECATED
```

### Сценарий 13.4: Нельзя депрецировать используемый конфиг

```gherkin
Scenario: Депрекация блокируется при активных процессах
  Given конфиг PUBLISHED
  And activeProcessCount = 5
  When user-admin пытается депрецировать
  Then возвращается ошибка BUSINESS_ERROR
```

### Сценарий 13.5: Snapshot-on-Start

```gherkin
Scenario: Процесс работает на зафиксированной версии
  Given процесс запущен с configVersion = 3
  When публикуется configVersion = 4
  Then процесс продолжает работать на версии 3
  And новые процессы используют версию 4
```

---

## 17. Группа 14: Интеграционные сценарии

**Убрано (ADR-024):** сценарии реакции модуля на события `substitution.started`/`substitution.ended` и на публикацию `approval.task.reassigned` — функциональность замещений убрана из модуля целиком. См. `11_adr.md` ADR-024.

### Сценарий 14.1: Пустой RoleResolverAdapter

```gherkin
Scenario: Модуль работает без RoleResolverAdapter
  Given пустой RoleResolverAdapter
  When инициатор генерирует маршрут из шаблона
  Then участники не заполняются автоматически
  And UI может заполнить вручную
```

### Сценарий 14.2: Снимок сущности обязателен

```gherkin
Scenario: Без EntityAdapter создание процесса невозможно
  Given EntityAdapter недоступен
  When инициатор создаёт процесс
  Then возвращается ошибка EntityAdapterException
```

### Сценарий 14.3: Outbox при недоступности RabbitMQ

```gherkin
Scenario: События сохраняются в Outbox при недоступности брокера
  Given RabbitMQ недоступен
  When происходит переход с публикацией события
  Then событие сохраняется в outbox_event
  And не теряется
  When RabbitMQ восстанавливается
  Then PublisherService публикует события
```

### Сценарий 14.4: Идемпотентность повторной публикации

```gherkin
Scenario: Подписчик получает дубль события
  Given событие было опубликовано 2 раза
  When подписчик обрабатывает
  Then он определяет дубль по eventId
  And не выполняет действие повторно
```

### Сценарий 14.5: Идемпотентность API по Idempotency-Key

```gherkin
Scenario: Повторный запрос с тем же Idempotency-Key не выполняется повторно
  Given клиент отправил POST /processes/process-1/start с заголовком Idempotency-Key = key-1
  And запрос успешно выполнен, результат сохранён в таблице idempotency_key
  When клиент повторно отправляет тот же запрос с Idempotency-Key = key-1 (например, из-за таймаута сети)
  Then сервис возвращает ранее сохранённый ответ без повторного выполнения перехода
  And ссылается на `08_db_schema.md` §20 / ADR-026
```

### Сценарий 14.6: Shedlock предотвращает двойной запуск

```gherkin
Scenario: Задача планировщика выполняется только один раз
  Given 3 экземпляра Approval Service
  When срабатывает расписание автоархивации
  Then только один экземпляр получает блокировку через таблицу shedlock
  And выполняет задачу
  And остальные не выполняют
```

---

## 18. Сводная таблица

| Группа | Кол-во сценариев | Ключевые проверки |
|---|---|---|
| 1. Создание и запуск | 5 | Старт, валидации, роли |
| 2. Согласование этапа | 8 | 5 режимов агрегации + Sequential (ADR-022) + авто |
| 3. Возврат на этап | 7 | Целевой этап, allowedReturnStages, программная инициация (ADR-025) |
| 4. Итерации этапа | 3 | Создание, вычисление |
| 5. Доп. согласующие | 8 | Иерархия, видимость, клонирование, рекомендация не унифицирована с Decision (ADR-016) |
| 6. Замечания и комментарии | 10 | Все статусы, валидации |
| 7. Отзыв и возобновление | 4 | STANDARD |
| 8. UNIFIED | 7 | Финальное решение, ответственный |
| 9. Финальное решение | 3 | Валидации, immutable |
| 10. Ревизии документа | 6 | Создание, прерывание, дубли |
| 11. Архивация | 5 | Авто, восстановление |
| 12. Агрегация для Front | 11 | approval-view, actions, remarks |
| 13. Конфигурация карт | 5 | Публикация, валидация, snapshot |
| 14. Интеграционные | 6 | Outbox, Idempotency-Key, Shedlock |
| **Итого** | **88** | — |

**Изменения от 2026-09-18:** добавлен сценарий 2.8 (Sequential/ADR-022), сценарий 3.7 (программная инициация StageRejectedByReturn/ADR-025), сценарий 5.5 (Decision/Recommendation не унифицированы/ADR-016), сценарий 14.5 (Idempotency-Key/ADR-026); убраны сценарии реакции на замещение (были 14.1–14.2, ADR-024), группа 14 перенумерована.

---

## Приложение А. Обозначения

| Обозначение | Что значит |
|---|---|
| `user-N` | Тестовый пользователь |
| `org-N` | Тестовая организация |
| `template-N` | Тестовый шаблон |
| `process-N` | Тестовый процесс |
| `stage-N` | Тестовый этап |
| `aa-N` | Доп. согласующий |
| `remark-N` | Замечание |
| `agg-N` | Агрегат документа |

---

## Приложение Б. Рекомендации по реализации тестов

| Тип теста | Что покрывает |
|---|---|
| **Unit** | Guards, Actions, сервисы (изолированно) |
| **Integration** | Взаимодействие с БД, адаптерами, RabbitMQ (Testcontainers) |
| **Contract** | События (AsyncAPI), API (OpenAPI) |
| **E2E** | Полные сценарии через REST API |
| **Property-based** | Инварианты (например, `allowedReturnStages` ⊆ прошлых) |

### Б.1 Использование Testcontainers

- PostgreSQL — реальная БД.
- RabbitMQ — реальный брокер.
- Redis — реальный кэш.

### Б.2 Моки адаптеров

- Пустые реализации по умолчанию.
- Mockito для точечных сценариев.
- WireMock для REST-адаптеров.

---
