# DEFECT-04 — Ошибка резолвинга бина не той реализации отдаётся как 500

> Найден при документировании кода (PR #17). Исправление — ветка `fix/defect-04-component-resolver`.
> Связанные тикеты: DEFECT-15 (коды реестров), DEFECT-06 (маппинг ошибок в HTTP).

## Статус

`Не начато`

## Контекст

Движок резолвит guard/action по строке `handler` из реестра. Если по имени находится бин
**другого типа** (например `handler` опечатан так, что совпал с именем постороннего бина),
Spring бросает `BeanNotOfRequiredTypeException`, которое `ComponentResolver` не обрабатывает:
исключение уходит наверх как есть и превращается в `500 INTERNAL_ERROR` без указания, какой
именно `handler` и какая карта переходов виноваты. Диагностика ошибки конфигурации занимает
часы вместо минуты, а доменного кода ошибки у клиента нет.

## Источники (для истории, не для перехода)

- `05_guards_actions_registry.md` §6.1 — как регистрируются guards/actions, роль поля `handler`.
- `05_guards_actions_registry.md` §7.2 — коды ошибок валидации (`GUARD_NOT_FOUND`, `ACTION_NOT_FOUND`).
- `10_architecture.md` §7.2 — компоненты State Machine Engine (резолвинг компонентов).

## Что уже есть в репозитории

- `src/main/java/ru/coordination/approval/engine/ComponentResolver.java` — резолвинг по имени бина
  и по полному имени класса.
- `src/main/java/ru/coordination/approval/engine/exception/ComponentResolutionException.java` —
  доменное исключение, которое уже используется для случая «бин не найден».
- `src/main/java/ru/coordination/approval/engine/registry/GuardRegistry.java`,
  `ActionRegistry.java` — вызывают резолвер при каждом переходе.
- `src/main/java/ru/coordination/approval/api/exception/GlobalExceptionHandler.java` — обработчик,
  который `ComponentResolutionException` сейчас относит к общей ветке `Exception` (500).

## Дефект

В `ComponentResolver` перехватывается только отсутствие бина:

```java
try {
    return context.getBean(handler, expectedType);
} catch (NoSuchBeanDefinitionException ex) {
    throw new ComponentResolutionException(...);
}
```

`BeanNotOfRequiredTypeException` (бин есть, но это не `Guard`/`Action`) не перехватывается:
исключение всплывает без контекста (`handler`, ожидаемый тип, `entityType`/`configId`) и
отдаётся клиенту как 500. Валидация карты (`TransitionMapValidator`) ловит это только на старте
приложения для `PUBLISHED`-карт; для черновиков и для карт, изменённых после старта, — нет.

## Явно не входит (Out of scope)

- Механизм hot-reload карт переходов: карта по-прежнему читается на каждый переход из БД.
- Добавление новых кодов ошибок валидации в `TransitionMapErrorCode` (см. DEFECT-15).

## Технические требования

1. Перехватывать в `ComponentResolver` оба случая: `NoSuchBeanDefinitionException` **и**
   `BeanNotOfRequiredTypeException` (а также `BeansException` как общий предок, если это не
   ломает другие сценарии). В `ComponentResolutionException` передавать: значение `handler`,
   ожидаемый тип (`Guard`/`Action`), `entityType`, `configId` и код компонента, если он известен
   вызывающему.
2. Текст сообщения должен позволять по логу однозначно найти строку реестра: минимум
   `handler`, ожидаемый тип, фактический тип бина (он есть в `BeanNotOfRequiredTypeException`).
3. `GlobalExceptionHandler`: добавить ветку для `ComponentResolutionException` →
   `409 CONFLICT` (ошибка конфигурации, а не клиентского запроса) с телом
   `ErrorResponse("COMPONENT_RESOLUTION_ERROR", ...)`. Если решено, что это `500` — обосновать в PR
   и всё равно исключить попадание в общую ветку.
4. Не менять контракт `resolve(String, Class<T>)`, чтобы не затронуть `GuardRegistry`/`ActionRegistry`.

## Критерии приёмки

1. Юнит-тест `ComponentResolverTest`: `ApplicationContext`, возвращающий бин другого типа, →
   `ComponentResolutionException` с упоминанием `handler` и фактического типа; отсутствие бина →
   прежнее поведение (тоже `ComponentResolutionException`).
2. Интеграционный тест: карта с `handler`, указывающим на посторонний бин, при переходе даёт
   `ComponentResolutionException`, а не `BeanNotOfRequiredTypeException`; при вызове через REST —
   предсказуемый код ответа (по п. 3).
3. В логе ошибки присутствуют `handler`, ожидаемый тип, `configId` — проверяется тестом на
   сообщение исключения.
4. `./gradlew build` зелёный.

## Тестирование

- Юнит: `ComponentResolverTest` (новый), существующие тесты движка
  (`TransitionEngineUnitTest`, `TransitionEngineIntegrationTest`) как регрессия.
- Интеграционный: `TransitionMapValidatorIntegrationTest` — дополнить кейсом «handler указывает
  на бин неверного типа» (валидатор должен это находить на старте, а резолвер — не падать
  неинформативно в рантайме).

## Открытые вопросы

1. Какой HTTP-код считать правильным для ошибки конфигурации карты: `409` (конфликт состояния
   конфигурации) или `500` (внутренняя ошибка сервера)? Тикет предлагает `409`.
2. Нужно ли валидации запрещать `handler`, который резолвится в бин неверного типа, уже на
   этапе публикации карты (сейчас проверяется только существование записи в реестре)?
