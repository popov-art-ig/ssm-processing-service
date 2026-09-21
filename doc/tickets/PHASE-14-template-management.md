# PHASE-14 — Шаблоны маршрутов (Template Management)

> **Статус:** ✅ Готово  
> **Story Points:** 13  
> **Приоритет:** 🔴 Критический путь до MVP

## Контекст

После завершения PHASE-01 до PHASE-08 и PHASE-11 у нас есть полностью рабочий процесс согласования типа STANDARD: процесс можно запустить, участники принимают решения, этапы закрываются по агрегации, возможен возврат на доработку, замечания работают. 

**Но процессы создаются программно** — через построение объектов `ProcessInstance`, `StageInstance`, `Participant` в коде тестов и сервисов. Для продакшена процессы должны создаваться из **шаблонов** через REST API.

Эта фаза реализует **управление шаблонами маршрутов**: создание, редактирование, версионирование, публикацию и снятие с публикации. Следующая фаза (PHASE-15) реализует генерацию `ProcessInstance` из шаблона по снимку сущности.

**Зависимости:**
- **Требует:** PHASE-04 (активация этапа — логика, которую шаблоны описывают)
- **Разблокирует:** PHASE-15 (генерация процессов из шаблонов)

## Источники (для истории, не для перехода)

- `03_domain_model.md` §6 (TemplateAggregate)
- `08_db_schema.md` §6 (template, stage_template, slot_template, applicability_rule)
- `10_architecture.md` §7.3 (TemplateService)
- `11_adr.md` ADR-023 (версионирование шаблонов без отдельной таблицы TemplateVersion)

## Объём фазы (Scope)

### Что входит

1. **TemplateService** — use-case сервис для управления шаблонами:
   - `create(TemplateCreateRequest)` — создание нового шаблона (статус Draft)
   - `update(templateId, TemplateUpdateRequest)` — изменение черновика или создание новой версии
   - `publish(templateId, actorId)` — публикация шаблона (Draft → Published)
   - `deprecate(templateId, actorId)` — снятие с публикации (Published → Deprecated)
   - `archive(templateId, actorId)` — архивация (Deprecated → Archived)
   - `getById(templateId)` — получение шаблона
   - `findByNameAndStatus(name, status)` — поиск шаблонов

2. **TemplateStateMachine** (конфигурация для state machine):
   - Переходы: `PublishTemplate`, `DeprecateTemplate`, `ArchiveTemplate`
   - Guards: `AllMandatorySlotsValid`, `NoActiveProcesses`
   - Actions: `ValidateTemplateStructure`, `SetPublishedTimestamp`, `SetArchivedTimestamp`

3. **Repositories**:
   - `TemplateRepository extends JpaRepository<Template, UUID>`
   - `StageTemplateRepository extends JpaRepository<StageTemplate, UUID>`
   - `SlotTemplateRepository extends JpaRepository<SlotTemplate, UUID>`
   - `ApplicabilityRuleRepository extends JpaRepository<ApplicabilityRule, UUID>`

4. **Миграция V30** — конфигурация TemplateStateMachine:
   - `state_machine_config` для `entity_type='TEMPLATE', process_type='N/A', version=1`
   - `state_config`: Draft, Published, Deprecated, Archived
   - `transition_config`: PublishTemplate (Draft → Published), DeprecateTemplate (Published → Deprecated), ArchiveTemplate (Deprecated → Archived)
   - Guards/Actions в registry

5. **Версионирование (ADR-023)**:
   - При `update()` опубликованного шаблона создаётся **новая строка** `Template` с:
     - Новым `id`
     - `version = старая.version + 1`
     - `parentTemplateId = старый.id`
     - Статусом `Draft`
   - Старая версия переходит в `Deprecated` (Fork & Drain)
   - `StageTemplate`, `SlotTemplate`, `ApplicabilityRule` копируются для новой версии
   - `ProcessInstance.templateRef` указывает на конкретный `Template.id` (Snapshot-on-Start)

6. **Валидация при публикации**:
   - Все обязательные слоты (`required=true`) имеют либо `userId`, либо `acceptableRoles` (не оба null)
   - Все этапы имеют `duration > 0`
   - Все `SlotTemplate` удовлетворяют check-констрейнту по `slotType` (см. ТТ)
   - `executionOrder` и `decisionMode` заполнены для STANDARD (для UNIFIED null)

## Явно не входит (Out of scope)

- ❌ **Генерация ProcessInstance из шаблона** → PHASE-15
- ❌ **Подбор шаблона по entitySnapshot** (MatchService) → PHASE-15
- ❌ **Резолвинг ролей в userId** (RoleResolverAdapter) → PHASE-15
- ❌ **REST API для шаблонов** → PHASE-24
- ❌ **ApplicabilityRule matching logic** → PHASE-15 (в этой фазе правила только сохраняются)
- ❌ **Удаление шаблонов** — шаблоны не удаляются, только архивируются

## Что уже есть в репозитории на момент постановки

1. **Таблицы БД** (созданы в PHASE-01, миграция V1):
   - `template` — [src/main/resources/db/migration/V1__initial_schema.sql](../../../src/main/resources/db/migration/V1__initial_schema.sql)
   - `stage_template`, `slot_template`, `applicability_rule`

2. **JPA-сущности** (созданы в PHASE-01):
   - `Template` — [src/main/java/ru/coordination/approval/domain/template/Template.java](../../../src/main/java/ru/coordination/approval/domain/template/Template.java)
   - `StageTemplate`, `SlotTemplate`, `ApplicabilityRule`
   - Enum `TemplateStatus`: DRAFT, PUBLISHED, DEPRECATED, ARCHIVED

3. **TransitionEngine** (PHASE-02):
   - [src/main/java/ru/coordination/approval/engine/TransitionEngine.java](../../../src/main/java/ru/coordination/approval/engine/TransitionEngine.java)

4. **ProcessService** (PHASE-03, PHASE-08):
   - Использует `ProcessInstance.templateRef` для Snapshot-on-Start
   - Логика `allowedReturnStages` уже работает с данными из `StageInstance` (которые будут клонироваться из `StageTemplate`)

## Технические требования

### 1. TemplateService

**Пакет:** `ru.coordination.approval.service`  
**Класс:** `TemplateService`

```java
@Service
@Transactional
@RequiredArgsConstructor
public class TemplateService {
    private final TemplateRepository templateRepository;
    private final StageTemplateRepository stageTemplateRepository;
    private final SlotTemplateRepository slotTemplateRepository;
    private final ApplicabilityRuleRepository applicabilityRuleRepository;
    private final TransitionEngine transitionEngine;
    private final StateMachineConfigRepository configRepository;
    
    /**
     * Создать новый шаблон в статусе Draft.
     * 
     * @param request данные для создания шаблона
     * @return созданный Template с заполненными stages, slots, rules
     */
    public Template create(TemplateCreateRequest request) {
        // 1. Создать Template со статусом DRAFT, version=1, parentTemplateId=null
        // 2. Создать StageTemplate[] для каждого этапа из request.stages
        // 3. Создать SlotTemplate[] для каждого слота
        // 4. Создать ApplicabilityRule[] из request.applicabilityRules
        // 5. Сохранить всё через репозитории (cascade persist)
        // 6. Вернуть Template
    }
    
    /**
     * Обновить шаблон. Если шаблон в статусе Draft — изменяет на месте.
     * Если Published/Deprecated — создаёт новую версию (Fork & Drain).
     * 
     * @param templateId идентификатор текущей версии
     * @param request изменения
     * @return обновлённый Template (тот же id для Draft, новый id для Published/Deprecated)
     */
    public Template update(UUID templateId, TemplateUpdateRequest request) {
        // 1. Найти Template
        // 2. Если status=DRAFT → обновить на месте (stages/slots/rules тоже)
        // 3. Если status=PUBLISHED или DEPRECATED:
        //    a) Создать новую строку Template с:
        //       - новым id
        //       - version = старая.version + 1
        //       - parentTemplateId = старый.id
        //       - status = DRAFT
        //    b) Скопировать все StageTemplate/SlotTemplate/ApplicabilityRule с новыми id
        //    c) Применить изменения из request к новым копиям
        //    d) Старую версию перевести в DEPRECATED через TransitionEngine
        // 4. Вернуть новый Template
    }
    
    /**
     * Опубликовать шаблон (Draft → Published).
     * Валидирует структуру перед публикацией.
     */
    public TransitionResult publish(UUID templateId, UUID actorId) {
        // 1. Найти Template
        // 2. Валидация: все обязательные слоты имеют userId или acceptableRoles
        // 3. Валидация: все этапы имеют duration > 0
        // 4. Валидация: executionOrder/decisionMode заполнены для STANDARD
        // 5. Вызвать TransitionEngine.transition(TEMPLATE, templateId, configId, "Draft", USER_ACTION, context, template::setStatus)
        //    где context содержит actorId
        // 6. Вернуть TransitionResult
    }
    
    /**
     * Снять с публикации (Published → Deprecated).
     * Fork & Drain: активные процессы продолжают использовать эту версию,
     * новые процессы используют другую версию.
     */
    public TransitionResult deprecate(UUID templateId, UUID actorId) {
        // Вызвать TransitionEngine для перехода DeprecateTemplate
    }
    
    /**
     * Архивировать шаблон (Deprecated → Archived).
     * Можно только если нет активных процессов на этом шаблоне.
     */
    public TransitionResult archive(UUID templateId, UUID actorId) {
        // Вызвать TransitionEngine для перехода ArchiveTemplate
        // Guard NoActiveProcesses проверит отсутствие процессов
    }
    
    public Optional<Template> getById(UUID templateId) {
        return templateRepository.findById(templateId);
    }
    
    public List<Template> findByNameAndStatus(String name, TemplateStatus status) {
        return templateRepository.findByNameAndStatus(name, status);
    }
}
```

**DTOs:**

```java
public record TemplateCreateRequest(
    String name,
    ProcessType processType,
    Boolean processIterationEnabled,
    UUID[] responsibleRoles, // для UNIFIED
    Boolean requiresResponsibleApproval, // для UNIFIED
    List<StageTemplateDto> stages,
    List<ApplicabilityRuleDto> applicabilityRules,
    UUID createdBy
) {}

public record TemplateUpdateRequest(
    String name,
    Boolean processIterationEnabled,
    UUID[] responsibleRoles,
    Boolean requiresResponsibleApproval,
    List<StageTemplateDto> stages,
    List<ApplicabilityRuleDto> applicabilityRules
) {}

public record StageTemplateDto(
    Integer orderIdx,
    String name,
    String description,
    StageType stageType,
    Integer duration,
    DecisionMode decisionMode,
    ExecutionOrder executionOrder,
    Boolean isMandatory,
    Boolean isOrderMandatory,
    Integer[] allowedReturnStages,
    List<SlotTemplateDto> actorSlots
) {}

public record SlotTemplateDto(
    Integer orderIdx,
    UUID userId, // может быть null
    UUID organizationId, // может быть null
    UUID[] acceptableRoles, // может быть null или пустой массив
    Boolean required,
    Boolean isUserEditable,
    Boolean isOrganizationEditable,
    Boolean isDeletable
) {}

public record ApplicabilityRuleDto(
    Integer ruleIdx,
    String[] entityTypes,
    String[] entitySubtypes,
    Map<String, Object> attributeConditions // JSON-like структура
) {}
```

### 2. Guards и Actions

**Пакет:** `ru.coordination.approval.service.guard`, `ru.coordination.approval.service.action`

#### Guard: AllMandatorySlotsValid

```java
@Component("allMandatorySlotsValid")
public class AllMandatorySlotsValidGuard implements Guard {
    private final SlotTemplateRepository slotTemplateRepository;
    
    @Override
    public boolean execute(TransitionContext context) {
        Template template = (Template) context.entity();
        // Найти все SlotTemplate для этого шаблона через stageTemplates
        // Проверить: для каждого слота с required=true
        //   либо userId != null, либо acceptableRoles не пусто
        // Вернуть true если все валидны, иначе false
    }
}
```

#### Guard: NoActiveProcesses

```java
@Component("noActiveProcesses")
public class NoActiveProcessesGuard implements Guard {
    private final ProcessRepository processRepository;
    
    @Override
    public boolean execute(TransitionContext context) {
        Template template = (Template) context.entity();
        // Найти процессы с templateRef = template.id и status не в терминальных статусах
        // Вернуть true если таких процессов нет
    }
}
```

#### Action: ValidateTemplateStructure

```java
@Component("validateTemplateStructure")
public class ValidateTemplateStructureAction implements Action {
    @Override
    public void execute(TransitionContext context) {
        Template template = (Template) context.entity();
        // Валидация:
        // 1. Все StageTemplate имеют duration > 0
        // 2. Для STANDARD: executionOrder и decisionMode не null
        // 3. Для UNIFIED: executionOrder и decisionMode null
        // 4. allowedReturnStages содержат только валидные orderIdx (≤ текущий)
        // 5. SlotTemplate удовлетворяют check-констрейнтам:
        //    - slotType=ACTOR => parentSlotId=null, stageTemplateId!=null
        //    - slotType=ADDITIONAL_APPROVER => parentSlotId!=null, stageTemplateId=null
        //    - isOrganizationEditable=false OR isUserEditable=true
        // Если что-то не валидно → throw IllegalArgumentException
    }
}
```

#### Action: SetPublishedTimestamp

```java
@Component("setPublishedTimestamp")
public class SetPublishedTimestampAction implements Action {
    private final TemplateRepository templateRepository;
    
    @Override
    public void execute(TransitionContext context) {
        Template template = (Template) context.entity();
        UUID actorId = context.actorId();
        template.setPublishedAt(Instant.now());
        template.setPublishedBy(actorId);
        templateRepository.save(template);
    }
}
```

#### Action: SetArchivedTimestamp

```java
@Component("setArchivedTimestamp")
public class SetArchivedTimestampAction implements Action {
    private final TemplateRepository templateRepository;
    
    @Override
    public void execute(TransitionContext context) {
        Template template = (Template) context.entity();
        template.setArchivedAt(Instant.now());
        templateRepository.save(template);
    }
}
```

### 3. Repositories

```java
public interface TemplateRepository extends JpaRepository<Template, UUID> {
    List<Template> findByNameAndStatus(String name, TemplateStatus status);
    
    @Query("SELECT t FROM Template t WHERE t.status = :status ORDER BY t.version DESC")
    List<Template> findByStatus(@Param("status") TemplateStatus status);
    
    @Query("SELECT t FROM Template t WHERE t.parentTemplateId = :parentId ORDER BY t.version DESC")
    List<Template> findChildVersions(@Param("parentId") UUID parentId);
}

public interface StageTemplateRepository extends JpaRepository<StageTemplate, UUID> {
    List<StageTemplate> findByTemplateIdOrderByOrderIdx(UUID templateId);
}

public interface SlotTemplateRepository extends JpaRepository<SlotTemplate, UUID> {
    List<SlotTemplate> findByStageTemplateId(UUID stageTemplateId);
}

public interface ApplicabilityRuleRepository extends JpaRepository<ApplicabilityRule, UUID> {
    List<ApplicabilityRule> findByTemplateIdOrderByRuleIdx(UUID templateId);
}
```

### 4. Миграция V30__seed_template_state_machine.sql

```sql
-- Guards
INSERT INTO guard_registry (guard_name, bean_name, description) VALUES
('AllMandatorySlotsValid', 'allMandatorySlotsValid', 'Все обязательные слоты имеют userId или acceptableRoles'),
('NoActiveProcesses', 'noActiveProcesses', 'Нет активных процессов на этом шаблоне');

-- Actions
INSERT INTO action_registry (action_name, bean_name, description) VALUES
('ValidateTemplateStructure', 'validateTemplateStructure', 'Валидирует структуру шаблона перед публикацией'),
('SetPublishedTimestamp', 'setPublishedTimestamp', 'Устанавливает publishedAt и publishedBy'),
('SetArchivedTimestamp', 'setArchivedTimestamp', 'Устанавливает archivedAt');

-- Статусы
INSERT INTO status_registry (code, entity_type, display_name, is_terminal) VALUES
('Draft', 'TEMPLATE', 'Черновик', false),
('Published', 'TEMPLATE', 'Опубликован', false),
('Deprecated', 'TEMPLATE', 'Снят с публикации', false),
('Archived', 'TEMPLATE', 'Архивирован', true);

-- State Machine Config
INSERT INTO state_machine_config (id, entity_type, process_type, version) VALUES
('a0000000-0000-0000-0000-000000000030', 'TEMPLATE', 'N/A', 1);

-- States
INSERT INTO state_config (id, state_machine_config_id, state_name) VALUES
(gen_random_uuid(), 'a0000000-0000-0000-0000-000000000030', 'Draft'),
(gen_random_uuid(), 'a0000000-0000-0000-0000-000000000030', 'Published'),
(gen_random_uuid(), 'a0000000-0000-0000-0000-000000000030', 'Deprecated'),
(gen_random_uuid(), 'a0000000-0000-0000-0000-000000000030', 'Archived');

-- Transitions
INSERT INTO transition_config (
    id, state_machine_config_id, transition_name, 
    from_state, to_state, trigger_type, 
    guards, actions, emitted_events
) VALUES
-- PublishTemplate (Draft → Published)
(gen_random_uuid(), 'a0000000-0000-0000-0000-000000000030', 'PublishTemplate',
 'Draft', 'Published', 'USER_ACTION',
 ARRAY['AllMandatorySlotsValid']::varchar[], 
 ARRAY['ValidateTemplateStructure', 'SetPublishedTimestamp']::varchar[],
 ARRAY['template.published']::varchar[]),

-- DeprecateTemplate (Published → Deprecated)
(gen_random_uuid(), 'a0000000-0000-0000-0000-000000000030', 'DeprecateTemplate',
 'Published', 'Deprecated', 'USER_ACTION',
 ARRAY[]::varchar[], ARRAY[]::varchar[], ARRAY['template.deprecated']::varchar[]),

-- ArchiveTemplate (Deprecated → Archived)
(gen_random_uuid(), 'a0000000-0000-0000-0000-000000000030', 'ArchiveTemplate',
 'Deprecated', 'Archived', 'USER_ACTION',
 ARRAY['NoActiveProcesses']::varchar[],
 ARRAY['SetArchivedTimestamp']::varchar[],
 ARRAY['template.archived']::varchar[]);
```

### 5. Алгоритм версионирования (ADR-023)

**При update() опубликованного шаблона:**

1. Найти текущий `Template` (старая версия, status=PUBLISHED или DEPRECATED)
2. Создать новую строку `Template`:
   ```java
   Template newVersion = Template.builder()
       .id(UUID.randomUUID()) // новый id
       .name(oldTemplate.getName())
       .processType(oldTemplate.getProcessType())
       .status(TemplateStatus.DRAFT) // всегда Draft
       .version(oldTemplate.getVersion() + 1)
       .parentTemplateId(oldTemplate.getId()) // ссылка на старую версию
       .processIterationEnabled(request.processIterationEnabled())
       .responsibleRoles(request.responsibleRoles())
       .requiresResponsibleApproval(request.requiresResponsibleApproval())
       .createdAt(Instant.now())
       .createdBy(oldTemplate.getCreatedBy()) // сохраняем автора первой версии
       .updatedAt(Instant.now())
       .build();
   ```

3. Скопировать все `StageTemplate`:
   ```java
   for (StageTemplate oldStage : oldTemplate.getStages()) {
       StageTemplate newStage = oldStage.toBuilder()
           .id(UUID.randomUUID())
           .template(newVersion)
           .build();
       // Применить изменения из request если нужно
       newVersion.getStages().add(newStage);
   }
   ```

4. Скопировать все `SlotTemplate` (с новыми stageTemplateId)
5. Скопировать все `ApplicabilityRule`
6. Применить изменения из `TemplateUpdateRequest` к новым копиям
7. Сохранить `newVersion` через `templateRepository.save()`
8. Старую версию перевести в DEPRECATED через `TransitionEngine`
9. Вернуть `newVersion`

**Fork & Drain:** старая версия остаётся в DEPRECATED, активные процессы доживают на ней (ProcessInstance.templateRef не меняется). Новые процессы создаются из новой версии.

### 6. Check-констрейнты SlotTemplate (уже в БД, проверяются в ValidateTemplateStructureAction)

```sql
-- В таблице slot_template уже есть:
CHECK (
    (slot_type = 'ACTOR' AND parent_slot_id IS NULL AND stage_template_id IS NOT NULL)
    OR
    (slot_type = 'ADDITIONAL_APPROVER' AND parent_slot_id IS NOT NULL AND stage_template_id IS NULL)
)

CHECK (is_organization_editable = false OR is_user_editable = true)
```

## Критерии приёмки

1. **Создание шаблона:**
   - `TemplateService.create()` создаёт Template со статусом DRAFT, version=1
   - Создаются StageTemplate, SlotTemplate, ApplicabilityRule
   - Все связи корректны (template ↔ stages ↔ slots)

2. **Публикация шаблона:**
   - `TemplateService.publish()` переводит Draft → Published
   - Валидация срабатывает: обязательные слоты имеют userId или acceptableRoles
   - Валидация срабатывает: duration > 0 для всех этапов
   - Если валидация не прошла → TransitionResult.performed=false

3. **Версионирование:**
   - `TemplateService.update()` для PUBLISHED шаблона создаёт новую версию:
     - Новый Template.id, version = старая+1, parentTemplateId = старый.id
     - Статус новой версии = DRAFT
     - Старая версия переходит в DEPRECATED
     - Все stages/slots/rules скопированы с новыми id

4. **Fork & Drain:**
   - После создания новой версии старая версия в DEPRECATED
   - ProcessInstance, созданные на старой версии, продолжают ссылаться на неё (templateRef не меняется)

5. **Снятие с публикации:**
   - `TemplateService.deprecate()` переводит Published → Deprecated
   - TransitionResult.performed=true, emittedEvents содержит 'template.deprecated'

6. **Архивация:**
   - `TemplateService.archive()` работает только если нет активных процессов
   - Guard NoActiveProcesses блокирует архивацию если есть процессы в статусах InProgress/OnRework/Draft

7. **Валидация структуры:**
   - ValidateTemplateStructureAction проверяет check-констрейнты SlotTemplate
   - Для STANDARD: executionOrder и decisionMode не null
   - Для UNIFIED: executionOrder и decisionMode null
   - allowedReturnStages содержат только orderIdx ≤ текущего этапа

8. **Поиск шаблонов:**
   - `TemplateService.findByNameAndStatus("Template A", PUBLISHED)` возвращает все опубликованные версии с этим именем
   - Можно найти latest версию (max version)

## Тестирование

### Юнит-тесты

- **AllMandatorySlotsValidGuardTest:**
  - required=true, userId=null, acceptableRoles=[] → guard возвращает false
  - required=true, userId=UUID → guard возвращает true
  - required=true, acceptableRoles=[roleId] → guard возвращает true
  - required=false, userId=null → guard возвращает true

- **NoActiveProcessesGuardTest:**
  - Mock ProcessRepository: нет процессов → guard возвращает true
  - Есть процесс в статусе InProgress → guard возвращает false
  - Есть процесс в статусе Approved (терминальный) → guard возвращает true

- **ValidateTemplateStructureActionTest:**
  - STANDARD шаблон с executionOrder=null → IllegalArgumentException
  - UNIFIED шаблон с executionOrder!=null → IllegalArgumentException
  - duration=0 → IllegalArgumentException
  - allowedReturnStages=[5] для этапа orderIdx=2 → IllegalArgumentException
  - SlotTemplate с slotType=ACTOR, но parentSlotId!=null → IllegalArgumentException

### Интеграционный тест

**TemplateServiceIntegrationTest** (Testcontainers, реальные миграции V1-V30):

1. **Создание и публикация шаблона:**
   ```java
   @Test
   void createsAndPublishesTemplate() {
       // Создать TemplateCreateRequest с 2 этапами, по 2 слота на каждом
       Template template = templateService.create(request);
       assertThat(template.getStatus()).isEqualTo(TemplateStatus.DRAFT);
       assertThat(template.getVersion()).isEqualTo(1);
       assertThat(template.getStages()).hasSize(2);
       
       // Публикация
       TransitionResult result = templateService.publish(template.getId(), actorId);
       assertThat(result.performed()).isTrue();
       
       Template published = templateService.getById(template.getId()).orElseThrow();
       assertThat(published.getStatus()).isEqualTo(TemplateStatus.PUBLISHED);
       assertThat(published.getPublishedAt()).isNotNull();
       assertThat(published.getPublishedBy()).isEqualTo(actorId);
   }
   ```

2. **Валидация блокирует публикацию:**
   ```java
   @Test
   void cannotPublishTemplateWithInvalidSlots() {
       // Создать шаблон с обязательным слотом без userId и acceptableRoles
       TemplateCreateRequest request = ...; // required=true, userId=null, acceptableRoles=[]
       Template template = templateService.create(request);
       
       TransitionResult result = templateService.publish(template.getId(), actorId);
       assertThat(result.performed()).isFalse(); // guard блокирует
   }
   ```

3. **Версионирование:**
   ```java
   @Test
   void createsNewVersionWhenUpdatingPublishedTemplate() {
       // Создать и опубликовать v1
       Template v1 = templateService.create(request);
       templateService.publish(v1.getId(), actorId);
       
       // Обновить
       TemplateUpdateRequest updateRequest = ...; // изменить название этапа
       Template v2 = templateService.update(v1.getId(), updateRequest);
       
       assertThat(v2.getId()).isNotEqualTo(v1.getId()); // новый id
       assertThat(v2.getVersion()).isEqualTo(2);
       assertThat(v2.getParentTemplateId()).isEqualTo(v1.getId());
       assertThat(v2.getStatus()).isEqualTo(TemplateStatus.DRAFT);
       
       // Старая версия deprecated
       Template v1Updated = templateService.getById(v1.getId()).orElseThrow();
       assertThat(v1Updated.getStatus()).isEqualTo(TemplateStatus.DEPRECATED);
   }
   ```

4. **Fork & Drain:**
   ```java
   @Test
   void existingProcessesKeepOldTemplateReference() {
       // Создать шаблон v1 и процесс на нём
       Template v1 = createAndPublishTemplate();
       ProcessInstance process = createProcessFromTemplate(v1.getId());
       assertThat(process.getTemplateRef()).isEqualTo(v1.getId());
       
       // Обновить шаблон → v2
       Template v2 = templateService.update(v1.getId(), updateRequest);
       
       // Процесс всё ещё ссылается на v1
       ProcessInstance reloaded = processRepository.findById(process.getId()).orElseThrow();
       assertThat(reloaded.getTemplateRef()).isEqualTo(v1.getId()); // не изменился
   }
   ```

5. **Архивация блокируется активными процессами:**
   ```java
   @Test
   void cannotArchiveTemplateWithActiveProcesses() {
       Template template = createAndPublishTemplate();
       ProcessInstance process = createProcessFromTemplate(template.getId());
       process.setStatus("InProgress");
       processRepository.save(process);
       
       templateService.deprecate(template.getId(), actorId);
       
       TransitionResult result = templateService.archive(template.getId(), actorId);
       assertThat(result.performed()).isFalse(); // guard NoActiveProcesses блокирует
   }
   ```

## Открытые вопросы

1. **Что делать с TemplateUpdateRequest если изменилось количество этапов?**
   - Вариант A: В request передаётся полный новый список этапов (перезаписываются все)
   - Вариант B: В request передаются только изменённые этапы + флаги добавления/удаления
   - **Решение:** Вариант A — полная перезапись. При создании новой версии копируются все этапы из request, старые игнорируются. Проще и однозначнее.

2. **Можно ли редактировать DEPRECATED шаблон?**
   - Вариант A: Да, создаётся новая версия (как с PUBLISHED)
   - Вариант B: Нет, только DRAFT можно редактировать
   - **Решение:** Вариант A. Если нужна новая версия deprecated шаблона — вызываем `update()`, создастся v3 в статусе DRAFT.

3. **Валидация ApplicabilityRule.attributeConditions — в этой фазе или в PHASE-15?**
   - JSON-структура сохраняется как есть, валидация операторов (gte, in, eq) — в PHASE-15 при matching.
   - В этой фазе только проверяем что это валидный JSON.

4. **Поведение при попытке publish() уже опубликованного шаблона:**
   - TransitionEngine вернёт `NoApplicableTransitionException` (нет перехода из Published по PublishTemplate).
   - Задокументировать в PR.

## Ревью Cowork

*(Заполняется после реализации и проверки)*
