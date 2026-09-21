package ru.coordination.approval.service;

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

        template.getStages().clear();
        template.getApplicabilityRules().clear();
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

    public TransitionResult publish(UUID templateId, UUID actorId) {
        return executeTransition(templateId, actorId);
    }

    public TransitionResult deprecate(UUID templateId, UUID actorId) {
        return executeTransition(templateId, actorId);
    }

    public TransitionResult archive(UUID templateId, UUID actorId) {
        return executeTransition(templateId, actorId);
    }

    private TransitionResult executeTransition(UUID templateId, UUID actorId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));

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
