# DEFECT-06 — Доменные ошибки шаблонов и подбора не мапятся в 4xx

> Найден при документировании кода (PR #17). Исправление — ветка `fix/defect-06-error-mapping`.
> Связанные тикеты: DEFECT-04 (ошибки резолвинга бина), DEFECT-11 (валидация маршрута).

## Статус

`Не начато`

## Контекст

Контракт API задаёт таблицу кодов ошибок, включая `NOT_FOUND` (404) и `BUSINESS_ERROR` (422).
`GlobalExceptionHandler` реализует только часть таблицы: бизнес-исключения модуля попадают в
общую ветку `Exception` и превращаются в `500 INTERNAL_ERROR` с обезличенным текстом
«An unexpected error occurred». Клиент не понимает, что произошло (нет шаблона? не разрешён
обязательный слот?), а причина остаётся только в логе.

## Источники (для истории, не для перехода)

- `07_api_contract.md` §9.2 (таблица кодов ошибок), §9.3 (бизнес-ошибки), §2.4 (коды ответов).
- `05_guards_actions_registry.md` §4.1–4.2 — условия, которые порождают бизнес-отказы.

## Что уже есть в репозитории

- `src/main/java/ru/coordination/approval/api/exception/GlobalExceptionHandler.java` —
  `VALIDATION_ERROR`(400), `NOT_FOUND`(404), `BAD_REQUEST`(400), `CONFLICT`(409),
  `INTERNAL_ERROR`(500).
- `src/main/java/ru/coordination/approval/exception/NoMatchingTemplateException.java` —
  бросается `MatchService.selectBestTemplate`.
- `src/main/java/ru/coordination/approval/exception/RequiredSlotNotResolvedException.java` —
  бросается `RouteValidatorService`.
- `src/main/java/ru/coordination/approval/exception/TemplateNotFoundException.java` —
  бросается `RouteGenerationService.selectTemplate` (при явно указанном шаблоне).
- `src/main/java/ru/coordination/approval/api/exception/EntityNotFoundException.java` — уже
  маппится в 404.
- `src/main/java/ru/coordination/approval/api/exception/ErrorResponse.java` — тело ответа.

## Дефект

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleInternalError(Exception ex) {
    log.error("Unhandled exception while processing request", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
}
```

Три бизнес-исключения не имеют веток и попадают сюда:

| Исключение | Где бросается | Что ожидает контракт |
|---|---|---|
| `NoMatchingTemplateException` | `MatchService.selectBestTemplate` | `BUSINESS_ERROR` 422 либо `NOT_FOUND` 404 (шаблон не найден) с текстом причины |
| `RequiredSlotNotResolvedException` | `RouteValidatorService.validateStage` | `BUSINESS_ERROR` 422 (обязательный слот не разрешён) |
| `TemplateNotFoundException` | `RouteGenerationService.selectTemplate` | `NOT_FOUND` 404 |

Дополнительно: в реализации вообще **нет** кода `BUSINESS_ERROR` (422), хотя он есть в §9.2 —
то есть бизнес-ошибки неотличимы от внутренних сбоев ни по коду, ни по HTTP-статусу.

## Явно не входит (Out of scope)

- Реализация `Idempotency-Key` и `IDEMPOTENCY_CONFLICT` (§7, §9.2) — отдельная задача.
- `UNAUTHORIZED`/`FORBIDDEN` — аутентификация и RBAC не реализованы (PHASE-21-25 вне объёма).
- Реализация заглушек `RemarkController`/`CommentController`/`AdminController`.

## Технические требования

1. Добавить ветки `@ExceptionHandler` для трёх исключений:
   - `NoMatchingTemplateException` → `422 BUSINESS_ERROR`, текст с `entityType`/`entitySubtype`
     (клиенту полезно знать, по каким данным шаблон не найден);
   - `RequiredSlotNotResolvedException` → `422 BUSINESS_ERROR`, текст с идентификатором слота и
     этапа;
   - `TemplateNotFoundException` → `404 NOT_FOUND`.
2. Ввести базовый тип для бизнес-исключений модуля (например `BusinessException extends
   RuntimeException` в `ru.coordination.approval.exception`) и обрабатывать его одной веткой →
   `422 BUSINESS_ERROR`, чтобы новые бизнес-ошибки не забывали маппинг. Существующие три
   исключения наследуются от него; ветка `Exception` остаётся последним рубежом для `500`.
3. Тексты в `message` для 4xx должны быть пригодны для пользователя (что именно не так), в
   отличие от `INTERNAL_ERROR`, где текст остаётся обезличенным.
4. Не менять формат `ErrorResponse` (`code`, `message`) — он уже описан и используется тестами.

## Критерии приёмки

1. Тест `GlobalExceptionHandlerTest`/`ProcessApiIntegrationTest`: `POST /api/v1/processes` при
   отсутствии подходящего шаблона возвращает `422` с `code = "BUSINESS_ERROR"`, а не `500`.
2. Тест: запрос шаблона по неизвестному `id` (`GET /api/v1/admin/templates/{id}`) и создание
   процесса по неизвестному `templateId` → `404 NOT_FOUND`.
3. Тест: обязательный слот без участника → `422 BUSINESS_ERROR` с идентификатором слота.
4. Реестр кодов в `GlobalExceptionHandler` соответствует §9.2: присутствуют все коды, которые
   сервис способен породить; расхождения перечислены в Javadoc класса.
5. `./gradlew build` зелёный; существующие тесты, ожидающие 400/404/409, не сломаны.

## Тестирование

- Юнит: прямые тесты обработчика на каждое исключение (MockMvc с `standaloneSetup` или
  интеграционные через `TestRestTemplate`).
- Интеграционные: `ProcessApiIntegrationTest`, `TemplateControllerIntegrationTest`,
  `RouteGenerationServiceTest` (последний — на уровне исключения, без HTTP).
- Регрессия: `ProcessControllerTest`, `DecisionControllerTest`.

## Открытые вопросы

1. Для «нет шаблона» по данным сущности: `422 BUSINESS_ERROR` или `404 NOT_FOUND`? Тикет
   предлагает `422` (запрос корректен, но применить нечего), однако §12.1 контракта может
   трактовать это как «ресурс не найден» — нужно подтверждение Cowork.
2. Нужно ли отдавать клиенту детальный текст причины или ограничиться кодом (вопрос раскрытия
   внутренней конфигурации шаблонов)?
