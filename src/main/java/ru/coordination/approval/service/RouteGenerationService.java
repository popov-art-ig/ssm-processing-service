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
import ru.coordination.approval.dto.generation.EntitySnapshot;
import ru.coordination.approval.dto.generation.RouteGenerationRequest;
import ru.coordination.approval.exception.TemplateNotFoundException;

@Service
@RequiredArgsConstructor
public class RouteGenerationService {

    /**
     * Начальный статус {@code ProcessInstance}. Должен совпадать с {@code from_state} перехода
     * {@code StartProcess} и {@code is_initial = true} в {@code state_config} для PROCESS
     * (миграция {@code V18__seed_start_process_transition.sql}). Ранее здесь стояло значение
     * {@code "Pending"}, из-за чего созданный процесс не мог быть запущен —
     * {@code TransitionEngine} не находил переход из состояния {@code Pending}.
     */
    static final String INITIAL_PROCESS_STATUS = "Draft";

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

        return generateRoute(request, entitySnapshot);
    }

    /**
     * Генерация маршрута по уже готовому снимку сущности.
     *
     * <p>Отдельная перегрузка нужна вызывающему коду, который получил атрибуты сущности не от
     * {@link EntityAdapter}, а из собственного запроса (например,
     * {@code POST /api/v1/processes} принимает {@code attributes} в теле). Без неё
     * {@code EntityAdapterStub} подменял бы реальные атрибуты запроса своими (см. аудит,
     * дефект «атрибуты запроса игнорируются при подборе шаблона»).
     */
    @Transactional
    public ProcessInstance generateRoute(RouteGenerationRequest request, EntitySnapshot entitySnapshot) {
        // 1. Select template
        Template template = selectTemplate(request.templateId(), entitySnapshot);

        // 2. Generate route
        ProcessInstance process = buildProcess(template, request, entitySnapshot);

        // 3. Validate — со сверкой обязательных слотов именно этого шаблона,
        // иначе необязательные слоты ошибочно считаются незаполненными обязательными.
        routeValidatorService.validate(process, template.getStages());

        // 4. Save
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
                // BaseEntity объявляет @Id без @GeneratedValue (id генерирует приложение),
                // поэтому id обязан быть проставлен до save(): иначе Hibernate падает с
                // "Identifier of entity ... must be manually assigned before calling 'persist()'".
                .id(UUID.randomUUID())
                .entityType(request.entityType())
                .entityId(request.entityId())
                .templateRef(template.getId())
                .processType(template.getProcessType())
                .configVersion(1)
                .status(INITIAL_PROCESS_STATUS)
                .initiatorId(request.initiatorId())
                .createdAt(now)
                .stages(new ArrayList<>())
                .build();

        Instant currentDueDate = now;

        for (StageTemplate stageTemplate : template.getStages()) {
            currentDueDate = currentDueDate.plus(stageTemplate.getDuration(), ChronoUnit.DAYS);

            StageInstance stage = StageInstance.builder()
                    // id задаёт приложение (BaseEntity без @GeneratedValue) — см. комментарий выше.
                    .id(UUID.randomUUID())
                    .process(process)
                    .orderIdx(stageTemplate.getOrderIdx())
                    .originalOrderIdx(stageTemplate.getOrderIdx())
                    .name(stageTemplate.getName())
                    .description(stageTemplate.getDescription())
                    .stageType(stageTemplate.getStageType())
                    .duration(stageTemplate.getDuration())
                    .executionOrder(stageTemplate.getExecutionOrder())
                    // Ранее mandatory/orderMandatory/allowedReturnStages не переносились из шаблона,
                    // из-за чего AllMandatorySlotsFilledGuard не видел обязательных этапов и
                    // возврат на этап терял список допустимых целей (находка аудита).
                    .mandatory(stageTemplate.isMandatory())
                    .orderMandatory(stageTemplate.isOrderMandatory())
                    .allowedReturnStages(stageTemplate.getAllowedReturnStages())
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
                    .id(UUID.randomUUID())
                    .stage(stage)
                    .iterationIdx(1)
                    // started_at объявлен NOT NULL в схеме (V4__stages_and_iterations.sql):
                    // начальная итерация создаётся сразу активной, поэтому время старта обязательно.
                    .startedAt(now)
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
                        // id задаёт приложение (BaseEntity без @GeneratedValue) — см. buildProcess.
                        .id(UUID.randomUUID())
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
