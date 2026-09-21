# Индекс тикетов разработки

**Версия:** 1.0  
**Дата:** 2026-09-21  
**Всего фаз:** 28 (4 завершено, 24 осталось)

---

## Статус тикетов

| Статус | Описание | Количество |
|--------|----------|------------|
| ✅ Завершено | Фаза реализована и смержена в main | 4 |
| 📋 Детальный тикет | Полный тикет по шаблону готов | 4 |
| 📝 Краткое описание | Есть только краткое описание | 19 |
| ⏳ Не начато | Ещё не планировалось | 1 |

---

## Группа A: Завершение базового флоу STANDARD

### PHASE-05: Принятие решения участником ✅📋
- **Тикет:** [PHASE-05-participant-decision.md](PHASE-05-participant-decision.md)
- **Статус:** Детальный тикет готов
- **SP:** 5
- **Зависимости:** PHASE-04
- **Что входит:** DecisionService.decide(), ParticipantStateMachine, запись decision/comment
- **Следующий шаг:** Реализация (рекомендуемая следующая фаза)

### PHASE-06: Агрегация решений и закрытие этапа ✅📋
- **Тикет:** [PHASE-06-stage-aggregation.md](PHASE-06-stage-aggregation.md)
- **Статус:** Детальный тикет готов
- **SP:** 8
- **Зависимости:** PHASE-05
- **Что входит:** EvaluateAggregation, guards агрегации, StageApproved/OnRework
- **Следующий шаг:** Реализация после PHASE-05

### PHASE-07: Активация следующего этапа и завершение процесса ✅📋
- **Тикет:** [PHASE-07-process-completion.md](PHASE-07-process-completion.md)
- **Статус:** Детальный тикет готов
- **SP:** 5
- **Зависимости:** PHASE-06
- **Что входит:** ActivateNextStage, ProcessApproved/ApprovedWithComments, CompleteProcess
- **Следующий шаг:** Реализация после PHASE-06

---

## Группа B: Возврат на доработку и возобновление (STANDARD)

### PHASE-08: Возврат на доработку и возобновление ✅📋
- **Тикет:** [PHASE-08-process-resume.md](PHASE-08-process-resume.md)
- **Статус:** Детальный тикет готов
- **SP:** 13
- **Зависимости:** PHASE-06
- **Что входит:** ProcessRework, ResumeProcess, создание итераций, ActivateTargetStage
- **Следующий шаг:** Реализация после PHASE-07

### PHASE-09: Отзыв и возобновление после отзыва 📝
- **Тикет:** [PHASES_09-28_BRIEF.md](PHASES_09-28_BRIEF.md#phase-09)
- **Статус:** Краткое описание
- **SP:** 3
- **Зависимости:** PHASE-07
- **Что входит:** RecallProcess, RevokeActiveTasks, TakeBackInWork
- **Следующий шаг:** Cowork подготовит детальный тикет

---

## Группа C: Дополнительные согласующие и замечания (STANDARD)

### PHASE-10: Дополнительные согласующие 📝
- **Тикет:** [PHASES_09-28_BRIEF.md](PHASES_09-28_BRIEF.md#phase-10)
- **Статус:** Краткое описание
- **SP:** 5
- **Зависимости:** PHASE-05
- **Что входит:** AdditionalApproverService, иерархия доп.согласующих, рекомендации
- **Следующий шаг:** Cowork подготовит детальный тикет

### PHASE-11: Замечания и комментарии ✅📋
- **Тикет:** [PHASE-11-remarks-lifecycle.md](PHASE-11-remarks-lifecycle.md)
- **Статус:** Детальный тикет готов
- **SP:** 8
- **Зависимости:** PHASE-05, PHASE-08
- **Что входит:** RemarkStateMachine, RemarkService, AllRemarksProcessed (реальный), AutoProcessOpenRemarks
- **Следующий шаг:** Реализация после PHASE-08

---

## Группа D: Единый процесс (UNIFIED)

### PHASE-12: UNIFIED — согласование маршрута ответственным 📝
- **Тикет:** [PHASES_09-28_BRIEF.md](PHASES_09-28_BRIEF.md#phase-12)
- **Статус:** Краткое описание
- **SP:** 5
- **Зависимости:** PHASE-07
- **Что входит:** SubmitRoute, ApproveRoute/RejectRoute, ResponsibleService
- **Следующий шаг:** Cowork подготовит детальный тикет

### PHASE-13: UNIFIED — финальное решение ответственного 📝
- **Тикет:** [PHASES_09-28_BRIEF.md](PHASES_09-28_BRIEF.md#phase-13)
- **Статус:** Краткое описание
- **SP:** 5
- **Зависимости:** PHASE-12, PHASE-07
- **Что входит:** AwaitFinalDecision, FinalDecisionService, FinalApprove/Reject
- **Следующий шаг:** Cowork подготовит детальный тикет

---

## Группа E: Создание процессов из шаблонов

### PHASE-14: Шаблоны маршрутов 📝
- **Тикет:** [PHASES_09-28_BRIEF.md](PHASES_09-28_BRIEF.md#phase-14)
- **Статус:** Краткое описание
- **SP:** 13
- **Зависимости:** PHASE-04
- **Что входит:** TemplateService, CRUD, версионирование, Fork & Drain
- **Следующий шаг:** Cowork подготовит детальный тикет

### PHASE-15: Подбор шаблона и генерация маршрута 📝
- **Тикет:** [PHASES_09-28_BRIEF.md](PHASES_09-28_BRIEF.md#phase-15)
- **Статус:** Краткое описание
- **SP:** 21 (самая большая фаза)
- **Зависимости:** PHASE-14
- **Что входит:** MatchService, RouteGeneratorService, резолвинг слотов, валидация
- **Следующий шаг:** Cowork подготовит детальный тикет

---

## Группа F: Автоматизация и планирование

### PHASE-16: Автосогласование по срокам 📝
- **Тикет:** [PHASES_09-28_BRIEF.md](PHASES_09-28_BRIEF.md#phase-16)
- **Статус:** Краткое описание
- **SP:** 8
- **Зависимости:** PHASE-06
- **Что входит:** Планировщик, AutoApproveParticipant/Stage, DeadlineExpired
- **Следующий шаг:** Cowork подготовит детальный тикет

### PHASE-17: Напоминания 📝
- **Тикет:** [PHASES_09-28_BRIEF.md](PHASES_09-28_BRIEF.md#phase-17)
- **Статус:** Краткое описание
- **SP:** 3
- **Зависимости:** PHASE-16, PHASE-19
- **Что входит:** NotificationSettings, ScheduleReminder, SendReminder
- **Следующий шаг:** Cowork подготовит детальный тикет

### PHASE-18: Автоархивация процессов 📝
- **Тикет:** [PHASES_09-28_BRIEF.md](PHASES_09-28_BRIEF.md#phase-18)
- **Статус:** Краткое описание
- **SP:** 3
- **Зависимости:** PHASE-07
- **Что входит:** AutoArchive (180 дней), AdminService.restore
- **Следующий шаг:** Cowork подготовит детальный тикет

---

## Группа G: Интеграции и публикация событий

### PHASE-19: Event Publisher 📝
- **Тикет:** [PHASES_09-28_BRIEF.md](PHASES_09-28_BRIEF.md#phase-19)
- **Статус:** Краткое описание
- **SP:** 8
- **Зависимости:** все domain-фазы
- **Что входит:** OutboxService, Outbox pattern, RabbitMQ, поллер, retry
- **Следующий шаг:** Cowork подготовит детальный тикет

### PHASE-20: Адаптеры 📝
- **Тикет:** [PHASES_09-28_BRIEF.md](PHASES_09-28_BRIEF.md#phase-20)
- **Статус:** Краткое описание
- **SP:** 8
- **Зависимости:** PHASE-15
- **Что входит:** EntityAdapter, RoleResolverAdapter, PermissionAdapter (реальные)
- **Следующий шаг:** Cowork подготовит детальный тикет

---

## Группа H: REST API

### PHASE-21: REST API — группа «Процессы» 📝
- **Тикет:** [PHASES_09-28_BRIEF.md](PHASES_09-28_BRIEF.md#phase-21)
- **Статус:** Краткое описание
- **SP:** 8
- **Зависимости:** PHASE-15, PHASE-07/08/09
- **Что входит:** ProcessController, POST/GET /processes, start/recall/resume
- **Следующий шаг:** Cowork подготовит детальный тикет

### PHASE-22: REST API — группа «Решения» 📝
- **Тикет:** [PHASES_09-28_BRIEF.md](PHASES_09-28_BRIEF.md#phase-22)
- **Статус:** Краткое описание
- **SP:** 3
- **Зависимости:** PHASE-05, PHASE-21
- **Что входит:** DecisionController, POST .../decide, GET .../decisions
- **Следующий шаг:** Cowork подготовит детальный тикет

### PHASE-23: REST API — группа «Замечания и комментарии» 📝
- **Тикет:** [PHASES_09-28_BRIEF.md](PHASES_09-28_BRIEF.md#phase-23)
- **Статус:** Краткое описание
- **SP:** 3
- **Зависимости:** PHASE-11, PHASE-21
- **Что входит:** RemarkController, CommentController, полный lifecycle через API
- **Следующий шаг:** Cowork подготовит детальный тикет

### PHASE-24: REST API — группа «Шаблоны и администрирование» 📝
- **Тикет:** [PHASES_09-28_BRIEF.md](PHASES_09-28_BRIEF.md#phase-24)
- **Статус:** Краткое описание
- **SP:** 5
- **Зависимости:** PHASE-14, PHASE-18, PHASE-17
- **Что входит:** TemplateController, AdminController, StateMachineConfigController
- **Следующий шаг:** Cowork подготовит детальный тикет

### PHASE-25: REST API — агрегированные эндпоинты для Front 📝
- **Тикет:** [PHASES_09-28_BRIEF.md](PHASES_09-28_BRIEF.md#phase-25)
- **Статус:** Краткое описание
- **SP:** 5
- **Зависимости:** PHASE-21-24
- **Что входит:** ApprovalViewController, /entities/{id}/approval-view, /tasks/my
- **Следующий шаг:** Cowork подготовит детальный тикет

---

## Группа I: Дополнительные возможности

### PHASE-26: Ревизии документа 📝
- **Тикет:** [PHASES_09-28_BRIEF.md](PHASES_09-28_BRIEF.md#phase-26)
- **Статус:** Краткое описание
- **SP:** 3
- **Зависимости:** PHASE-08, PHASE-15
- **Что входит:** ProcessService.createRevision, InterruptOriginalProcess
- **Следующий шаг:** Cowork подготовит детальный тикет

### PHASE-27: Sequential execution order 📝
- **Тикет:** [PHASES_09-28_BRIEF.md](PHASES_09-28_BRIEF.md#phase-27)
- **Статус:** Краткое описание
- **SP:** 3
- **Зависимости:** PHASE-05
- **Что входит:** AssignNextParticipantTask, PreviousParticipantDecided
- **Следующий шаг:** Cowork подготовит детальный тикет

### PHASE-28: Дополнительные guards и actions 📝
- **Тикет:** [PHASES_09-28_BRIEF.md](PHASES_09-28_BRIEF.md#phase-28)
- **Статус:** Краткое описание
- **SP:** 5
- **Зависимости:** все domain-фазы
- **Что входит:** UserInOrganization, EntityAttributeMatch, LogAuditDetails и др.
- **Следующий шаг:** Cowork подготовит детальный тикет

---

## Завершённые фазы (уже в репозитории)

### PHASE-01: Базовая инфраструктура ✅
- **Тикет:** Не требуется (завершено)
- **Статус:** Смержено в main
- **Что реализовано:** Схема БД (27 таблиц), доменные сущности, репозитории

### PHASE-02: State Machine Engine ✅
- **Тикет:** [PHASE-02-state-machine-engine.md](PHASE-02-state-machine-engine.md)
- **Статус:** Смержено в main (#3)
- **Что реализовано:** TransitionEngine, ModelFactory, ComponentResolver, guards/actions registry

### PHASE-03: ProcessService.startProcess ✅
- **Тикет:** [PHASE-03-process-service-start.md](PHASE-03-process-service-start.md)
- **Статус:** Смержено в main (#5)
- **Что реализовано:** ProcessService, переход StartProcess, guards для STANDARD

### PHASE-04: Активация первого этапа ✅
- **Тикет:** [PHASE-04-stage-activation.md](PHASE-04-stage-activation.md)
- **Статус:** Смержено в main (#6)
- **Что реализовано:** ActivateStage, AssignStageTasks, AssignParticipantTasks, расчёт сроков

---

## Быстрая навигация

### По приоритету

**🔴 Критический путь (MVP):**
- PHASE-05, 06, 07 (базовый флоу)
- PHASE-08 (возврат)
- PHASE-11 (замечания)
- PHASE-14, 15 (шаблоны)
- PHASE-19 (события)
- PHASE-21 (API)

**🟡 Высокий приоритет:**
- PHASE-10 (доп.согласующие)
- PHASE-12, 13 (UNIFIED)
- PHASE-16 (автосогласование)

**🟢 Средний приоритет:**
- PHASE-09 (отзыв)
- PHASE-17, 18 (напоминания, архивация)
- PHASE-20 (адаптеры)
- PHASE-22, 23, 24, 25 (REST API)

**⚪ Низкий приоритет:**
- PHASE-26, 27, 28 (дополнительные возможности)

### По состоянию тикета

**Готовы к реализации (детальные тикеты):**
1. [PHASE-05-participant-decision.md](PHASE-05-participant-decision.md) — **рекомендуется следующим**
2. [PHASE-06-stage-aggregation.md](PHASE-06-stage-aggregation.md)
3. [PHASE-07-process-completion.md](PHASE-07-process-completion.md)
4. [PHASE-08-process-resume.md](PHASE-08-process-resume.md)
5. [PHASE-11-remarks-lifecycle.md](PHASE-11-remarks-lifecycle.md)

**Требуют подготовки детального тикета:**
- PHASE-09, 10, 12-28 (см. [PHASES_09-28_BRIEF.md](PHASES_09-28_BRIEF.md))

---

## Статистика

| Показатель | Значение |
|------------|----------|
| Всего фаз | 28 |
| Завершено | 4 (14%) |
| Детальные тикеты готовы | 4 (следующие 5 фаз) |
| Краткие описания | 19 |
| Story Points всего | 174 |
| Story Points завершено | 23 (13%) |
| Story Points осталось | 151 (87%) |
| Оценка времени (1 разработчик) | ~25 недель |
| Оценка времени (команда 2-3 чел) | ~12 недель |

---

## Процесс работы с тикетами

1. **Выбор следующей фазы:**
   - Рекомендуется идти по критическому пути: PHASE-05 → 06 → 07 → 08 → 11 → 14 → 15 → 19 → 21
   - Или выбрать по приоритету задачи

2. **Если есть детальный тикет:**
   - Claude Code открывает тикет
   - Реализует в отдельной ветке `feature/phase-N-<slug>`
   - Запускает тесты (`./gradlew build`)
   - Открывает PR в main

3. **Если только краткое описание:**
   - Cowork готовит детальный тикет по шаблону [TEMPLATE.md](TEMPLATE.md)
   - Учитывает текущее состояние кода после предыдущих фаз
   - Пользователь размещает тикет в репозитории
   - Далее по п.2

4. **После мержа PR:**
   - Cowork сверяет реализацию со спецификацией
   - Фиксирует расхождения (если есть) через addendum в ADR
   - Обновляет `REMAINING_PHASES.md` и `PHASES_SUMMARY.md` (опционально)

---

## Связанные документы

- [TEMPLATE.md](TEMPLATE.md) — шаблон для детальных тикетов
- [REMAINING_PHASES.md](../REMAINING_PHASES.md) — полный план оставшихся фаз с обоснованием
- [PHASES_SUMMARY.md](../PHASES_SUMMARY.md) — краткая сводка всех фаз
- [ROADMAP.md](../ROADMAP.md) — дорожная карта по кварталам
- [doc/arch/](../arch/) — архитектурная документация (контекст для тикетов)
- [doc/tasks/](../tasks/) — разбивка завершённых фаз на задачи

---
