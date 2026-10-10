package ru.coordination.approval.service;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.EntityType;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.statemachine.StateMachineConfig;
import ru.coordination.approval.domain.statemachine.TriggerType;
import ru.coordination.approval.domain.template.ApplicabilityRule;
import ru.coordination.approval.domain.template.SlotTemplate;
import ru.coordination.approval.domain.template.SlotType;
import ru.coordination.approval.domain.template.StageTemplate;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.TransitionEngine;
import ru.coordination.approval.engine.TransitionResult;
import ru.coordination.approval.engine.model.StateMachineConfigRepository;
import ru.coordination.approval.service.dto.ApplicabilityRuleDto;
import ru.coordination.approval.service.dto.SlotTemplateDto;
import ru.coordination.approval.service.dto.StageTemplateDto;
import ru.coordination.approval.service.dto.TemplateCreateRequest;
import ru.coordination.approval.service.dto.TemplateUpdateRequest;

/**
 * Управление шаблонами маршрутов (PHASE-14).
 * Создание, редактирование, версионирование (ADR-023, Fork & Drain), публикация и архивация.
 *
 * <p>Работает с агрегатом шаблона ({@code TemplateAggregate}: {@code Template},
 * {@code StageTemplate}, {@code SlotTemplate}, {@code ApplicabilityRule}, {@code 03_domain_model.md}
 * §6). Жизненный цикл — {@code DRAFT -> PUBLISHED -> DEPRECATED -> ARCHIVED} по карте
 * {@code TEMPLATE}/{@code processType = null}/v1 (миграция V25); статус хранится как
 * {@link LifecycleStatus}.
 *
 * <p><b>Версионирование вместо правки (ADR-023).</b> Отдельной сущности «версия шаблона» нет:
 * каждая версия — своя строка {@code Template} со своим {@code id}, {@code version + 1} и
 * {@code parentTemplateId}, а {@code StageTemplate}/{@code ApplicabilityRule} принадлежат
 * конкретной строке и пересоздаются с новыми идентификаторами. Поэтому {@link #update} для
 * не-{@code DRAFT} шаблонов не меняет существующую строку, а создаёт новую, и ссылки уже
 * запущенных процессов ({@code ProcessInstance.templateRef}) продолжают указывать на прежнюю
 * версию (Snapshot-on-Start, ADR-003).
 *
 * <p><b>Почему предусловия по статусу проверяются явно.</b> Движок выбирает переход по текущему
 * состоянию, а из одного состояния по {@code USER_ACTION} бывает несколько исходов; без проверки
 * вызов «опубликовать» на опубликованном шаблоне молча выполнял бы депрекацию. Это тот же приём,
 * что в {@code ProcessService.startProcess}/{@code resume} (см. {@link #executeTransition}).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final TransitionEngine transitionEngine;
    private final StateMachineConfigRepository configRepository;
    private final EntityManager entityManager;

    /**
     * Создаёт шаблон: новая строка {@code Template} в статусе {@code DRAFT}, {@code version = 1},
     * {@code parentTemplateId = null} вместе со структурой этапов, слотов и правил применимости
     * ({@code 07_api_contract.md} §10.3).
     *
     * <p>Статус проставляется напрямую, минуя движок: перехода {@code CreateTemplate} в карте V25
     * нет (перечисление его в {@code README.md} — неточность описания), поэтому ни guards, ни
     * {@code ValidateTemplateStructure} при создании не выполняются. Полная проверка структуры
     * происходит только при {@link #publish}.
     *
     * @param request описание шаблона; {@code stages}/{@code applicabilityRules} могут быть
     *                {@code null} — тогда шаблон создаётся без структуры
     * @return сохранённый черновик
     * @throws IllegalArgumentException если у этапа {@code duration <= 0} или
     *                                  {@code allowedReturnStages} ссылается на будущий этап: запись
     *                                  отклоняется до обращения к БД, чтобы клиент получил 400, а не
     *                                  500 от констрейнта {@code stage_template_duration_check}
     */
    public Template create(TemplateCreateRequest request) {
        Instant now = Instant.now();

        Template template = Template.builder()
                .id(UUID.randomUUID())
                .name(request.name())
                .processType(request.processType())
                .status(LifecycleStatus.DRAFT)
                .version(1)
                .parentTemplateId(null)
                .processIterationEnabled(Boolean.TRUE.equals(request.processIterationEnabled()))
                .responsibleRoles(request.responsibleRoles())
                .requiresResponsibleApproval(Boolean.TRUE.equals(request.requiresResponsibleApproval()))
                .createdAt(now)
                .createdBy(request.createdBy())
                .updatedAt(now)
                .stages(new ArrayList<>())
                .applicabilityRules(new ArrayList<>())
                .build();

        applyStages(template, request.stages());
        applyApplicabilityRules(template, request.applicabilityRules());

        return templateRepository.save(template);
    }

    /**
     * Изменяет шаблон в зависимости от его статуса: {@code DRAFT} правится на месте, любой другой
     * статус приводит к созданию новой версии (ADR-023; {@code README.md}, «Шаблоны: жизненный
     * цикл»).
     *
     * <p>Для опубликованного шаблона дополнительно выполняется Fork &amp; Drain в его сервисной
     * части: новая версия создаётся в {@code DRAFT}, а прежняя снимается с публикации
     * ({@link #deprecate}), потому что она больше не должна выбираться автоподбором.
     *
     * <p><b>Известное расхождение.</b> {@code 07_api_contract.md} §10.4 объявляет условием
     * обновления статус {@code DRAFT}. Фактически новая версия создаётся и для {@code PUBLISHED}, и
     * для {@code DEPRECATED} — так решает открытый вопрос 2 тикета PHASE-14; не покрыт решением
     * только {@code ARCHIVED}, для которого код ведёт себя так же, а снятие с публикации не
     * выполняется, потому что статус не {@code PUBLISHED}.
     *
     * @param templateId идентификатор конкретной версии шаблона
     * @param request    новые значения; {@code name = null} при создании новой версии сохраняет имя
     *                   предыдущей
     * @return изменённый черновик или новая версия в статусе {@code DRAFT}
     * @throws IllegalArgumentException если шаблона нет или структура не проходит проверку перед
     *                                  записью
     */
    public Template update(UUID templateId, TemplateUpdateRequest request) {
        Template current = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        if (current.getStatus() == LifecycleStatus.DRAFT) {
            return updateInPlace(current, request);
        }

        return createNewVersion(current, request);
    }

    /**
     * Правит черновик на месте, пересоздавая структуру этапов и правил (см. комментарий о
     * {@code flush} ниже: без принудительного сброса вставка новых строк опережает удаление старых и
     * падает на уникальном ключе {@code ux_stage_template_order}).
     */
    private Template updateInPlace(Template template, TemplateUpdateRequest request) {
        template.setName(request.name());
        template.setProcessIterationEnabled(Boolean.TRUE.equals(request.processIterationEnabled()));
        template.setResponsibleRoles(request.responsibleRoles());
        template.setRequiresResponsibleApproval(Boolean.TRUE.equals(request.requiresResponsibleApproval()));
        template.setUpdatedAt(Instant.now());

        // Очистка с последующим flush: Hibernate по умолчанию выполняет INSERT раньше DELETE, и без
        // принудительного сброса новые этапы вставлялись до удаления старых, падая на уникальном
        // ключе stage_template (template_id, order_idx) — «duplicate key value violates unique
        // constraint ux_stage_template_order». После flush удаление уходит в БД первым, и
        // пересоздание структуры проходит.
        template.getStages().clear();
        template.getApplicabilityRules().clear();
        entityManager.flush();

        applyStages(template, request.stages());
        applyApplicabilityRules(template, request.applicabilityRules());

        return templateRepository.save(template);
    }

    /**
     * Создаёт следующую версию шаблона вместо правки опубликованной строки (ADR-023):
     * {@code version + 1}, {@code parentTemplateId = oldTemplate.id}, статус {@code DRAFT}. Автором
     * остаётся {@code createdBy} предыдущей версии, а не тот, кто вызвал обновление.
     *
     * <p>Снятие прежней версии с публикации — часть Fork &amp; Drain, но выполняется только для
     * {@code PUBLISHED}, и происходит уже после сохранения новой версии.
     */
    private Template createNewVersion(Template oldTemplate, TemplateUpdateRequest request) {
        Instant now = Instant.now();

        Template newVersion = Template.builder()
                .id(UUID.randomUUID())
                .name(request.name() != null ? request.name() : oldTemplate.getName())
                .processType(oldTemplate.getProcessType())
                .status(LifecycleStatus.DRAFT)
                .version(oldTemplate.getVersion() + 1)
                .parentTemplateId(oldTemplate.getId())
                .processIterationEnabled(Boolean.TRUE.equals(request.processIterationEnabled()))
                .responsibleRoles(request.responsibleRoles())
                .requiresResponsibleApproval(Boolean.TRUE.equals(request.requiresResponsibleApproval()))
                .createdAt(now)
                .createdBy(oldTemplate.getCreatedBy())
                .updatedAt(now)
                .stages(new ArrayList<>())
                .applicabilityRules(new ArrayList<>())
                .build();

        applyStages(newVersion, request.stages());
        applyApplicabilityRules(newVersion, request.applicabilityRules());

        Template saved = templateRepository.save(newVersion);

        // Fork & Drain: старую опубликованную версию снять с публикации
        if (oldTemplate.getStatus() == LifecycleStatus.PUBLISHED) {
            deprecate(oldTemplate.getId(), oldTemplate.getCreatedBy());
        }

        return saved;
    }

    /**
     * Форк шаблона ({@code 07_api_contract.md} §10.6, ADR-023): новая строка {@code Template} с
     * {@code version + 1} и {@code parentTemplateId = templateId}, в статусе {@code DRAFT}.
     *
     * <p>Копируются структура ({@code stages} → {@code actorSlots}) и правила применимости.
     * Исходный шаблон остаётся как есть — в отличие от {@link #update} опубликованного шаблона,
     * форк не снимает его с публикации: снятие выполняет Fork &amp; Drain, когда новая версия
     * будет опубликована.
     *
     * @param templateId идентификатор исходной версии в любом статусе
     * @param actorId кто выполняет форк; при {@code null} берётся {@code createdBy} оригинала
     * @return новая версия в статусе {@code DRAFT} со скопированной структурой
     * @throws IllegalArgumentException если исходного шаблона нет или структура не проходит
     *                                  проверку перед записью
     *
     * <p><b>Известный пробел.</b> Обещанное выше снятие прежней версии с публикации не реализовано
     * и по факту не происходит: {@code PublishTemplate} в карте V25 не содержит actions, которые
     * депрецировали бы предыдущую версию цепочки, поэтому «Fork &amp; Drain при публикации» остаётся
     * ручным действием администратора.
     */
    public Template fork(UUID templateId, UUID actorId) {
        Template source = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        Instant now = Instant.now();

        Template forked = Template.builder()
                .id(UUID.randomUUID())
                .name(source.getName())
                .processType(source.getProcessType())
                .status(LifecycleStatus.DRAFT)
                .version(source.getVersion() + 1)
                .parentTemplateId(source.getId())
                .processIterationEnabled(source.isProcessIterationEnabled())
                .responsibleRoles(source.getResponsibleRoles() == null
                        ? null
                        : new ArrayList<>(source.getResponsibleRoles()))
                .requiresResponsibleApproval(source.isRequiresResponsibleApproval())
                .createdAt(now)
                .createdBy(actorId != null ? actorId : source.getCreatedBy())
                .updatedAt(now)
                .stages(new ArrayList<>())
                .applicabilityRules(new ArrayList<>())
                .build();

        // Копируем структуру через applyStages/applyApplicabilityRules: они пересоздают строки
        // с новыми id и ссылками на новую версию (stage_template.template_id указывает на
        // конкретную версию, поэтому переиспользовать дочерние строки нельзя — ADR-023).
        applyStages(forked, toStageDtos(source.getStages()));
        applyApplicabilityRules(forked, toRuleDtos(source.getApplicabilityRules()));

        return templateRepository.save(forked);
    }

    /**
     * Переводит доменные этапы в DTO, чтобы {@link #fork} пересоздал структуру, а не переиспользовал
     * дочерние строки исходной версии.
     */
    private List<StageTemplateDto> toStageDtos(List<StageTemplate> stages) {
        return stages.stream()
                .map(stage -> new StageTemplateDto(
                        stage.getOrderIdx(),
                        stage.getName(),
                        stage.getDescription(),
                        stage.getStageType(),
                        stage.getDuration(),
                        stage.getDecisionMode(),
                        stage.getExecutionOrder(),
                        stage.isMandatory(),
                        stage.isOrderMandatory(),
                        stage.getAllowedReturnStages() == null
                                ? null
                                : new ArrayList<>(stage.getAllowedReturnStages()),
                        stage.getActorSlots().stream()
                                .map(slot -> new SlotTemplateDto(
                                        slot.getOrderIdx(),
                                        slot.getUserId(),
                                        slot.getOrganizationId(),
                                        slot.getAcceptableRoles() == null
                                                ? null
                                                : new ArrayList<>(slot.getAcceptableRoles()),
                                        slot.isRequired(),
                                        slot.isUserEditable(),
                                        slot.isOrganizationEditable(),
                                        slot.isDeletable()))
                                .toList()))
                .toList();
    }

    /** Переводит доменные правила применимости в DTO для копирования в новую версию. */
    private List<ApplicabilityRuleDto> toRuleDtos(List<ApplicabilityRule> rules) {
        return rules.stream()
                .map(rule -> new ApplicabilityRuleDto(
                        rule.getRuleIdx(),
                        rule.getEntityTypes() == null ? null : new ArrayList<>(rule.getEntityTypes()),
                        rule.getEntitySubtypes() == null ? null : new ArrayList<>(rule.getEntitySubtypes()),
                        rule.getAttributeConditions()))
                .toList();
    }

    /**
     * Публикация шаблона: {@code DRAFT -> PUBLISHED}.
     *
     * <p>Предусловие по статусу проверяется явно. Без него {@code publish} на уже опубликованном
     * шаблоне не падал: движок выбирает переход по ТЕКУЩЕМУ состоянию, а из {@code PUBLISHED} по
     * {@code USER_ACTION} доступна депрекация — и вызов «опубликовать» молча снимал шаблон с
     * публикации. Та же защита, что в {@code ProcessService.startProcess}/{@code resume}.
     *
     * <p>Защита нужна именно поэтому: открытый вопрос 4 тикета PHASE-14 ожидал, что повторная
     * публикация упадёт с {@code NoApplicableTransitionException} из-за отсутствия перехода
     * {@code PublishTemplate} из {@code PUBLISHED}. Движок ищет переход по паре
     * {@code (fromState, trigger)}, а не по коду, поэтому кандидатом становится {@code DeprecateTemplate}
     * — без явной проверки статуса повторная публикация не упала бы, а сняла шаблон с публикации.
     *
     * <p>Guard {@code AllMandatorySlotsValid} и action {@code ValidateTemplateStructure} (V25)
     * проверяют структуру только в момент публикации: обязательность {@code executionOrder}/
     * {@code decisionMode} для STANDARD, запрет для UNIFIED, корректность {@code allowedReturnStages}
     * и наличие {@code stageTemplate} у слотов — то есть черновик может быть неконсистентным, а
     * опубликоваться обязан согласованным.
     *
     * @param actorId кто публикует; попадает в {@code publishedBy} через action
     *                {@code SetPublishedTimestamp}
     * @return результат перехода; {@code performed == false} — структура не прошла валидацию
     * @throws IllegalArgumentException если шаблона нет
     * @throws IllegalStateException если статус не {@code DRAFT}
     */
    public TransitionResult publish(UUID templateId, UUID actorId) {
        return executeTransition(templateId, actorId, LifecycleStatus.DRAFT, "publish");
    }

    /**
     * Депрекация шаблона: {@code PUBLISHED -> DEPRECATED} (Fork &amp; Drain). Депрецированная версия
     * больше не участвует в автоподборе, но остаётся доступной уже запущенным процессам.
     *
     * <p><b>Известное расхождение.</b> {@code 07_api_contract.md} §10.7 объявляет дополнительным
     * условием «нет активных процессов»; в коде оно не проверяется — guard {@code NoActiveProcesses}
     * навешен только на архивацию.
     *
     * @throws IllegalArgumentException если шаблона нет
     * @throws IllegalStateException если статус не {@code PUBLISHED}
     */
    public TransitionResult deprecate(UUID templateId, UUID actorId) {
        return executeTransition(templateId, actorId, LifecycleStatus.PUBLISHED, "deprecate");
    }

    /**
     * Архивирование шаблона: {@code DEPRECATED -> ARCHIVED}. Guard {@code NoActiveProcesses} (V25) не
     * даёт архивировать версию, на которой ещё идут процессы; событие в контракт не добавлено,
     * поэтому {@code emits} у перехода пуст (комментарий миграции V25).
     *
     * @throws IllegalArgumentException если шаблона нет
     * @throws IllegalStateException если статус не {@code DEPRECATED}
     */
    public TransitionResult archive(UUID templateId, UUID actorId) {
        return executeTransition(templateId, actorId, LifecycleStatus.DEPRECATED, "archive");
    }

    /**
     * Общий шаг всех переходов жизненного цикла: проверяет предусловие по статусу, берёт карту
     * {@code TEMPLATE} (единственная версия 1, {@code processType = null}) и применяет переход,
     * возвращая статус строки enum-значением.
     *
     * <p>Предусловие здесь — не формальность: из {@code PUBLISHED} по {@code USER_ACTION} доступна
     * депрекация, поэтому без явной проверки операция с неверным предусловием не упала бы, а
     * выполнила бы чужой переход.
     *
     * @param requiredStatus статус, в котором шаблон обязан находиться
     * @param operation      имя операции для текста ошибки
     * @throws IllegalArgumentException если шаблона нет
     * @throws IllegalStateException если карты переходов нет или статус не совпал с ожидаемым
     */
    private TransitionResult executeTransition(
            UUID templateId, UUID actorId, LifecycleStatus requiredStatus, String operation) {

        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        if (template.getStatus() != requiredStatus) {
            throw new IllegalStateException(
                    "Cannot %s template %s: expected status %s but was %s"
                            .formatted(operation, templateId, requiredStatus, template.getStatus()));
        }

        StateMachineConfig config = configRepository
                .findByEntityTypeAndProcessTypeAndVersion(EntityType.TEMPLATE, null, 1)
                .orElseThrow(() -> new IllegalStateException("Template state machine config not found"));

        TransitionContext context = new TransitionContext(template, actorId, ActorType.USER, Map.of());

        return transitionEngine.transition(
                EntityType.TEMPLATE,
                template.getId(),
                config.getId(),
                template.getStatus().name(),
                TriggerType.USER_ACTION,
                context,
                newState -> template.setStatus(LifecycleStatus.valueOf(newState)));
    }

    /**
     * Чтение шаблона по {@code id} конкретной версии; пустой {@code Optional} — версии нет, решение
     * об ответе принимает вызывающий код (контроллер отдаёт 404).
     */
    public Optional<Template> getById(UUID templateId) {
        return templateRepository.findById(templateId);
    }

    /**
     * Точное совпадение по имени и статусу. Список шаблонов в REST API фильтруется иначе — через
     * {@code Specification} в контроллере (поиск по подстроке имени, {@code 07_api_contract.md}
     * §10.1), поэтому этот метод в обработке запроса не участвует и вызывается только напрямую.
     */
    public List<Template> findByNameAndStatus(String name, LifecycleStatus status) {
        return templateRepository.findByNameAndStatus(name, status);
    }

    /**
     * Пересоздаёт этапы шаблона с новыми идентификаторами: строки {@code StageTemplate} принадлежат
     * конкретной версии шаблона, переиспользовать их между версиями нельзя (ADR-023). {@code null}
     * означает «структуру не менять».
     */
    private void applyStages(Template template, List<StageTemplateDto> stageDtos) {
        if (stageDtos == null) {
            return;
        }

        Instant now = Instant.now();
        for (StageTemplateDto stageDto : stageDtos) {
            // Проверка до записи: БД ловит duration <= 0 констрейнтом stage_template_duration_check,
            // и клиент получал бы 500 (DataIntegrityViolationException) вместо 400 с указанием
            // проблемы. allowedReturnStages > orderIdx констрейнтом не покрыт вовсе.
            validateStageForPersist(stageDto);

            StageTemplate stage = StageTemplate.builder()
                    .id(UUID.randomUUID())
                    .template(template)
                    .orderIdx(stageDto.orderIdx())
                    .name(stageDto.name())
                    .description(stageDto.description())
                    .stageType(stageDto.stageType())
                    .duration(stageDto.duration())
                    .decisionMode(stageDto.decisionMode())
                    .executionOrder(stageDto.executionOrder())
                    .mandatory(Boolean.TRUE.equals(stageDto.isMandatory()))
                    .orderMandatory(Boolean.TRUE.equals(stageDto.isOrderMandatory()))
                    .allowedReturnStages(stageDto.allowedReturnStages())
                    .createdAt(now)
                    .actorSlots(new ArrayList<>())
                    .build();

            applySlots(stage, stageDto.actorSlots());
            template.getStages().add(stage);
        }
    }

    /**
     * Проверяет данные этапа до записи, чтобы клиент получал 400 с указанием проблемы, а не 500 от
     * констрейнта БД (см. комментарий в теле): {@code duration > 0} и {@code allowedReturnStages}
     * только из прошлых и текущего этапов.
     *
     * @throws IllegalArgumentException если срок не положителен или цель возврата указывает вперёд
     */
    private void validateStageForPersist(StageTemplateDto stage) {
        // Имя необязательно (03_domain_model.md §6.4), поэтому в сообщении используется orderIdx —
        // иначе клиент видел бы «Stage 'null'».
        String label = stage.name() != null && !stage.name().isBlank()
                ? "Stage '%s' (orderIdx=%s)".formatted(stage.name(), stage.orderIdx())
                : "Stage orderIdx=%s".formatted(stage.orderIdx());

        if (stage.duration() != null && stage.duration() <= 0) {
            throw new IllegalArgumentException(
                    "%s must have duration > 0 but was %s".formatted(label, stage.duration()));
        }

        if (stage.allowedReturnStages() != null && stage.orderIdx() != null) {
            for (Integer returnStageIdx : stage.allowedReturnStages()) {
                if (returnStageIdx != null && returnStageIdx > stage.orderIdx()) {
                    throw new IllegalArgumentException(
                            ("%s allowedReturnStages contains %s which is greater than the stage orderIdx"
                                    + " (allowedReturnStages may reference only past and current stages)")
                                    .formatted(label, returnStageIdx));
                }
            }
        }
    }

    /**
     * Создаёт слоты этапа, разворачивая «не задано» ({@code null}) в значения по умолчанию:
     * {@code required} и {@code userEditable} — {@code true}, остальные флаги — {@code false}.
     *
     * <p><b>Известное ограничение.</b> {@code slotType} всегда {@code ACTOR}: тип слота в
     * {@link SlotTemplateDto} не передаётся, поэтому создать шаблон с доп. согласующими
     * ({@code ADDITIONAL_APPROVER}, PHASE-10) через сервис нельзя.
     */
    private void applySlots(StageTemplate stage, List<SlotTemplateDto> slotDtos) {
        if (slotDtos == null) {
            return;
        }

        Instant now = Instant.now();
        for (SlotTemplateDto slotDto : slotDtos) {
            SlotTemplate slot = SlotTemplate.builder()
                    .id(UUID.randomUUID())
                    .stageTemplate(stage)
                    .slotType(SlotType.ACTOR)
                    .orderIdx(slotDto.orderIdx())
                    .userId(slotDto.userId())
                    .organizationId(slotDto.organizationId())
                    .acceptableRoles(slotDto.acceptableRoles() != null ? slotDto.acceptableRoles() : new ArrayList<>())
                    .required(slotDto.required() == null || slotDto.required())
                    .userEditable(slotDto.isUserEditable() == null || slotDto.isUserEditable())
                    .organizationEditable(Boolean.TRUE.equals(slotDto.isOrganizationEditable()))
                    .deletable(Boolean.TRUE.equals(slotDto.isDeletable()))
                    .createdAt(now)
                    .children(new ArrayList<>())
                    .build();

            stage.getActorSlots().add(slot);
        }
    }

    /**
     * Создаёт правила применимости новой версии; {@code null} в списках внутри правила заменяется на
     * пустые коллекции, иначе {@code MatchService} не смог бы отличить «нет ограничения» от
     * {@code null} при разборе условий.
     */
    private void applyApplicabilityRules(Template template, List<ApplicabilityRuleDto> ruleDtos) {
        if (ruleDtos == null) {
            return;
        }

        Instant now = Instant.now();
        for (ApplicabilityRuleDto ruleDto : ruleDtos) {
            ApplicabilityRule rule = ApplicabilityRule.builder()
                    .id(UUID.randomUUID())
                    .template(template)
                    .ruleIdx(ruleDto.ruleIdx())
                    .entityTypes(ruleDto.entityTypes() != null ? ruleDto.entityTypes() : List.of())
                    .entitySubtypes(ruleDto.entitySubtypes() != null ? ruleDto.entitySubtypes() : List.of())
                    .attributeConditions(ruleDto.attributeConditions() != null ? ruleDto.attributeConditions() : Map.of())
                    .createdAt(now)
                    .build();

            template.getApplicabilityRules().add(rule);
        }
    }
}
