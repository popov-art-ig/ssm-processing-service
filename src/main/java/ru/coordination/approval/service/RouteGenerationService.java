package ru.coordination.approval.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.coordination.approval.adapter.EntityAdapter;
import ru.coordination.approval.adapter.RoleResolverAdapter;
import ru.coordination.approval.domain.common.DecisionMode;
import ru.coordination.approval.domain.common.StageType;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.ParticipantRole;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.ProcessRepository;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.domain.template.SlotTemplate;
import ru.coordination.approval.domain.template.SlotType;
import ru.coordination.approval.domain.template.StageTemplate;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;
import ru.coordination.approval.dto.EntitySnapshot;
import ru.coordination.approval.dto.RouteGenerationRequest;
import ru.coordination.approval.exception.TemplateNotFoundException;

@Service
@RequiredArgsConstructor
public class RouteGenerationService {

    private final TemplateRepository templateRepository;
    private final ProcessRepository processRepository;
    private final MatchService matchService;
    private final RouteValidatorService routeValidatorService;
    private final EntityAdapter entityAdapter;
    private final RoleResolverAdapter roleResolverAdapter;

    @Transactional
    public ProcessInstance generateRoute(RouteGenerationRequest request) {
        // 1. Fetch entity snapshot
        EntitySnapshot entitySnapshot = entityAdapter.fetchEntitySnapshot(
                request.entityType(),
                request.entityId()
        );

        // 2. Select template
        Template template = selectTemplate(request.templateId(), entitySnapshot);

        // 3. Generate route
        ProcessInstance process = buildProcess(template, request, entitySnapshot);

        // 4. Validate
        routeValidatorService.validate(process);

        // 5. Save
        return processRepository.save(process);
    }

    private Template selectTemplate(UUID templateId, EntitySnapshot entitySnapshot) {
        if (templateId != null) {
            // Explicit template
            return templateRepository.findById(templateId)
                    .orElseThrow(() -> new TemplateNotFoundException(templateId));
        } else {
            // Auto-select by rules
            List<Template> matching = matchService.findMatchingTemplates(entitySnapshot);
            return matchService.selectBestTemplate(matching);
        }
    }

    private ProcessInstance buildProcess(
            Template template,
            RouteGenerationRequest request,
            EntitySnapshot entitySnapshot
    ) {
        Instant now = Instant.now();

        ProcessInstance process = ProcessInstance.builder()
                .entityType(request.entityType())
                .entityId(request.entityId())
                .templateRef(template.getId())
                .processType(template.getProcessType())
                .configVersion(1)
                .status("Pending")
                .initiatorId(request.initiatorId())
                .createdAt(now)
                .stages(new ArrayList<>())
                .build();

        Instant currentDueDate = now;

        for (StageTemplate stageTemplate : template.getStages()) {
            currentDueDate = currentDueDate.plus(stageTemplate.getDuration(), ChronoUnit.DAYS);

            StageInstance stage = StageInstance.builder()
                    .process(process)
                    .orderIdx(stageTemplate.getOrderIdx())
                    .originalOrderIdx(stageTemplate.getOrderIdx())
                    .stageType(stageTemplate.getStageType())
                    .duration(stageTemplate.getDuration())
                    .executionOrder(stageTemplate.getExecutionOrder())
                    .decisionMode(stageTemplate.getDecisionMode() != null
                            ? stageTemplate.getDecisionMode()
                            : DecisionMode.AND)
                    .status("Pending")
                    .dueAt(currentDueDate)
                    .createdAt(now)
                    .iterations(new ArrayList<>())
                    .build();

            // Create initial iteration
            StageIteration iteration = StageIteration.builder()
                    .stage(stage)
                    .iterationIdx(1)
                    .status("Pending")
                    .createdAt(now)
                    .participants(new ArrayList<>())
                    .build();

            // Resolve participants from slots
            List<Participant> participants = resolveParticipants(
                    stageTemplate.getActorSlots(),
                    iteration,
                    request,
                    entitySnapshot
            );
            iteration.getParticipants().addAll(participants);

            stage.getIterations().add(iteration);
            process.getStages().add(stage);
        }

        return process;
    }

    private List<Participant> resolveParticipants(
            List<SlotTemplate> actorSlots,
            StageIteration iteration,
            RouteGenerationRequest request,
            EntitySnapshot entitySnapshot
    ) {
        List<Participant> participants = new ArrayList<>();
        Instant now = Instant.now();

        for (SlotTemplate slot : actorSlots) {
            List<UUID> userIds = resolveSlot(slot, request, entitySnapshot);

            for (UUID userId : userIds) {
                Participant participant = Participant.builder()
                        .stageIteration(iteration)
                        .userId(userId)
                        .role(mapSlotTypeToRole(slot.getSlotType()))
                        .orderIdx(slot.getOrderIdx())
                        .status("Assigned")
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                participants.add(participant);
            }
        }

        return participants;
    }

    private List<UUID> resolveSlot(
            SlotTemplate slot,
            RouteGenerationRequest request,
            EntitySnapshot entitySnapshot
    ) {
        if (!slot.getAcceptableRoles().isEmpty()) {
            // Resolve via RoleResolverAdapter using first acceptable role
            UUID roleId = slot.getAcceptableRoles().get(0);
            return roleResolverAdapter.resolveRole(
                    roleId,
                    request.organizationId(),
                    entitySnapshot
            );
        } else if (slot.getUserId() != null) {
            // Fixed user
            return List.of(slot.getUserId());
        } else {
            // Empty slot (will be resolved later or filled manually)
            return List.of();
        }
    }

    private ParticipantRole mapSlotTypeToRole(SlotType slotType) {
        return switch (slotType) {
            case ACTOR -> ParticipantRole.APPROVER;
            case ADDITIONAL_APPROVER -> ParticipantRole.ADDITIONAL_APPROVER;
        };
    }
}
