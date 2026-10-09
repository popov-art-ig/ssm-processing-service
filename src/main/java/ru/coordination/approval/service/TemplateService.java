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
 */
@Service
@Transactional
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final TransitionEngine transitionEngine;
    private final StateMachineConfigRepository configRepository;
    private final EntityManager entityManager;

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

    public Template update(UUID templateId, TemplateUpdateRequest request) {
        Template current = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

        if (current.getStatus() == LifecycleStatus.DRAFT) {
            return updateInPlace(current, request);
        }

        return createNewVersion(current, request);
    }

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
     * @param actorId кто выполняет форк; при {@code null} берётся {@code createdBy} оригинала
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
     */
    public TransitionResult publish(UUID templateId, UUID actorId) {
        return executeTransition(templateId, actorId, LifecycleStatus.DRAFT, "publish");
    }

    /** Депрекация шаблона: {@code PUBLISHED -> DEPRECATED} (Fork &amp; Drain). */
    public TransitionResult deprecate(UUID templateId, UUID actorId) {
        return executeTransition(templateId, actorId, LifecycleStatus.PUBLISHED, "deprecate");
    }

    /** Архивирование шаблона: {@code DEPRECATED -> ARCHIVED}. */
    public TransitionResult archive(UUID templateId, UUID actorId) {
        return executeTransition(templateId, actorId, LifecycleStatus.DEPRECATED, "archive");
    }

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

    public Optional<Template> getById(UUID templateId) {
        return templateRepository.findById(templateId);
    }

    public List<Template> findByNameAndStatus(String name, LifecycleStatus status) {
        return templateRepository.findByNameAndStatus(name, status);
    }

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
