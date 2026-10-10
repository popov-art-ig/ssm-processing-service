# DEFECT-08 — Поля-заглушки участника и рекомендаций доп. согласующего

> Найден при документировании кода (PR #17). Исправление — ветка `fix/defect-08-placeholders`.
> Связанные тикеты: DEFECT-02 (агрегация), DEFECT-07 (итерации), DEFECT-17 (мёртвый код).

## Статус

`Не начато`

## Контекст

В доменной модели есть поля, которые выглядят как рабочие данные, но их не пишет и не читает ни
один компонент: `Participant.decision` (ссылка на результат решения), `Participant.decidedAt`,
флаги редактируемости слота у участника, `AdditionalApprover.recommendation`. Плюс
`DecisionMapper` возвращает `decidedBy = null`, потому что «в `Decision` нет автора» — при этом
автор выводится из `participant` и мог бы заполняться. Такие поля вводят в заблуждение: по
модели кажется, что решение хранится у участника, а фактический источник истины — таблица
`decision`.

## Источники (для истории, не для перехода)

- `03_domain_model.md` §7 — модель участника, решения, доп. согласующего.
- `08_db_schema.md` §9 (participant), §10 (additional_approver), §11 (decision).
- `11_adr.md` ADR-015 (реестры статусов и результатов решений), ADR-016 (`Decision` и
  `Recommendation` — раздельные механизмы), ADR-018 (иерархия доп. согласующих).
- `05_guards_actions_registry.md` §5.4 — `A-PA-001 RecordDecision`.

## Что уже есть в репозитории

- `src/main/java/ru/coordination/approval/domain/process/Participant.java` — `decision`,
  `decidedAt`, `userEditable`, `organizationEditable`, `deletable`, `organizationId`.
- `src/main/java/ru/coordination/approval/domain/process/AdditionalApprover.java` —
  `recommendation`, `level`, `dueAt`, `dueOffset`.
- `src/main/java/ru/coordination/approval/domain/process/Decision.java` — фактическое решение
  (`result`, `comment`, `recordedAt`).
- `src/main/java/ru/coordination/approval/service/action/RecordDecisionAction.java` — пишет
  `Decision`, а не поля `Participant`.
- `src/main/java/ru/coordination/approval/api/mapper/DecisionMapper.java` — `hasDecision`
  (поиск по `participantId`) и `toDto` с `decidedBy = null`.
- `src/main/java/ru/coordination/approval/api/mapper/ProcessMapper.java` — N+1 из-за
  `hasDecision` на каждого участника.

## Дефекты в объёме тикета

### 1. `Participant.decision` / `Participant.decidedAt` — дубликаты без писателя

`RecordDecisionAction` создаёт строку в `decision` и не трогает поля участника; движок меняет
только `participant.status`. Значит `decision`/`decidedAt` всегда `null`. При этом Javadoc
модели и `08_db_schema.md` §9 описывают их как ссылку на результат решения. Любой код, который
начнёт на них опираться, получит пустоту (например фильтр «участники с решением»).

### 2. `DecisionMapper.hasDecision` — N+1 и неявный контракт

```java
public Boolean hasDecision(Participant participant) {
    return decisionRepository.findById(participant.getId()).isPresent();
}
```

- метод вызывается на каждого участника в `ProcessMapper.toParticipantDto` → N+1 при выводе
  процесса с несколькими этапами и участниками (целевое требование UI — ответ быстрее 500 мс
  на процесс из 10 этапов);
- поиск идёт по `participant.id`, то есть контракт «PK решения = PK участника» (так пишет
  `RecordDecisionAction`), но нигде не задокументирован как обязательный;
- возвращается `Boolean`, хотя `null` невозможен.

### 3. `decidedBy` всегда `null` в ответе

`DecisionDto.decidedBy` не заполняется: в `Decision` нет автора, но он однозначно выводится из
`participant.userId`. Клиент не может показать, кто принял решение, если у участника несколько
ролей/замещений.

### 4. `AdditionalApprover` не создаётся вовсе

Сущность и её поля (`level`, `recommendation`, `dueAt`, `dueOffset`) не заполняются ни одним
компонентом: механизм доп. согласующих — PHASE-10. Поля выглядят рабочими, но данные в них
никогда не появляются; `recommendation` (ADR-016, отдельный от `Decision` механизм) не имеет ни
писателя, ни читателя.

## Явно не входит (Out of scope)

- Реализация PHASE-10 (иерархия доп. согласующих, рекомендации) — тикет только приводит модель
  в честное состояние.
- Замещения (ADR-024 — функциональность отклонена).

## Технические требования

1. **Принять решение по каждому полю** и зафиксировать его в PR:
   - `Participant.decision`, `decidedAt` — либо начать заполнять (в `RecordDecisionAction`,
     в той же транзакции), либо пометить `@Deprecated`/`@Transient` и описать в Javadoc, что
     источник истины — `decision`, а поля оставлены для совместимости схемы. Удаление колонок
     требует миграции — если решено удалить, оформить отдельной задачей.
   - `AdditionalApprover.recommendation` и остальные поля — оставить как задел PHASE-10, но
     явно указать это в Javadoc (сделано в PR #17) и, при необходимости, закрыть от случайного
     использования (`@Deprecated`).
2. `DecisionMapper`: перейти на пакетную проверку — добавить в `DecisionRepository` метод
   `findByParticipantIdIn(Collection<UUID>)` (уже есть `findByParticipantIdIn(List<UUID>)`) и
   вычислять `hasDecision` без запроса на каждого участника. `ProcessMapper.toDto` должен
   получить карту решений один раз на процесс.
3. Заполнять `DecisionDto.decidedBy` из `participant.userId` (или изменить контракт DTO и убрать
   поле — решение зафиксировать).
4. `hasDecision` вернуть `boolean` либо задокументировать, что `null` не возвращается.

## Критерии приёмки

1. Вывод процесса с 10 этапами × 3 участниками не выполняет запрос к `decision` на каждого
   участника: проверяется тестом со счётчиком запросов (Hibernate `Statistics`) либо
   интеграционным тестом на время/количество запросов.
2. `GET /api/v1/processes/{id}` возвращает `hasDecision = true` для проголосовавшего участника и
   `false` для остальных — интеграционный тест.
3. `decidedBy` заполнен (или поле удалено из DTO осознанно, и это описано в PR).
4. Для каждого поля-заглушки в Javadoc есть явная пометка: заполняется/не заполняется, кем,
   в какой фазе появится писатель.
5. `./gradlew build` зелёный.

## Тестирование

- Юнит: `DecisionMapperTest` (если отсутствует — создать), `ProcessMapperTest` — пакетная
  загрузка решений.
- Интеграционные: `ProcessApiIntegrationTest` — `hasDecision`/`decidedBy` после решения;
  `ApprovalViewService` — отсутствие N+1 на агрегированном эндпоинте.
- Регрессия: `DecisionControllerTest`, `ProcessControllerTest`.

## Открытые вопросы

1. **Заполнять ли `Participant.decision`/`decidedAt`?** Если да — они должны обновляться
   действием `RecordDecision` (сейчас его нет в реестре под таким именем), а не только
   `Decision`. Если нет — колонки стоит удалить отдельной миграцией, но это уже изменение схемы.
2. **Убирать ли `decidedBy`** из `DecisionDto` вместо заполнения (в `Decision` автора нет, и
   ADR-019 требует только идентификаторы)?
3. Нужен ли `AdditionalApprover` вообще, если PHASE-10 не в ближайшем плане (не удалить ли
   сущность вместе с таблицей)?
