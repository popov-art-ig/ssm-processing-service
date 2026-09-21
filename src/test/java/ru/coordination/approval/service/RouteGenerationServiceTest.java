package ru.coordination.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.coordination.approval.adapter.EntityAdapter;
import ru.coordination.approval.adapter.RoleResolverAdapter;
import ru.coordination.approval.domain.common.DecisionMode;
import ru.coordination.approval.domain.common.ExecutionOrder;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.common.StageType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.ProcessRepository;
import ru.coordination.approval.domain.template.SlotTemplate;
import ru.coordination.approval.domain.template.SlotType;
import ru.coordination.approval.domain.template.StageTemplate;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;
import ru.coordination.approval.dto.EntitySnapshot;
import ru.coordination.approval.dto.RouteGenerationRequest;
import ru.coordination.approval.exception.TemplateNotFoundException;

@ExtendWith(MockitoExtension.class)
class RouteGenerationServiceTest {

    @Mock
    private TemplateRepository templateRepository;

    @Mock
    private ProcessRepository processRepository;

    @Mock
    private MatchService matchService;

    @Mock
    private RouteValidatorService routeValidatorService;

    @Mock
    private EntityAdapter entityAdapter;

    @Mock
    private RoleResolverAdapter roleResolverAdapter;

    private RouteGenerationService routeGenerationService;

    @BeforeEach
    void setUp() {
        routeGenerationService = new RouteGenerationService(
                templateRepository,
                processRepository,
                matchService,
                routeValidatorService,
                entityAdapter,
                roleResolverAdapter
        );
    }

    @Test
    void generatesRouteWithExplicitTemplate() {
        UUID templateId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID initiatorId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        RouteGenerationRequest request = new RouteGenerationRequest(
                "CONTRACT",
                entityId,
                templateId,
                organizationId,
                initiatorId
        );

        EntitySnapshot snapshot = new EntitySnapshot(
                "CONTRACT",
                null,
                entityId,
                Map.of(),
                Instant.now()
        );

        Template template = createTemplateWithStages(roleId);

        when(entityAdapter.fetchEntitySnapshot("CONTRACT", entityId))
                .thenReturn(snapshot);
        when(templateRepository.findById(templateId))
                .thenReturn(Optional.of(template));
        when(roleResolverAdapter.resolveRole(eq(roleId), eq(organizationId), any()))
                .thenReturn(List.of(userId1, userId2));
        when(processRepository.save(any(ProcessInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProcessInstance result = routeGenerationService.generateRoute(request);

        assertThat(result.getEntityType()).isEqualTo("CONTRACT");
        assertThat(result.getEntityId()).isEqualTo(entityId);
        assertThat(result.getTemplateId()).isEqualTo(templateId);
        assertThat(result.getStatus()).isEqualTo("Pending");
        assertThat(result.getStages()).hasSize(2);

        // Check first stage
        assertThat(result.getStages().get(0).getOrderIdx()).isEqualTo(1);
        assertThat(result.getStages().get(0).getDuration()).isEqualTo(3);
        assertThat(result.getStages().get(0).getIterations()).hasSize(1);
        assertThat(result.getStages().get(0).getIterations().get(0).getParticipants())
                .hasSize(2);

        verify(routeValidatorService).validate(any(ProcessInstance.class));
        verify(processRepository).save(any(ProcessInstance.class));
    }

    @Test
    void generatesRouteWithAutoSelection() {
        UUID entityId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID initiatorId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        RouteGenerationRequest request = new RouteGenerationRequest(
                "CONTRACT",
                entityId,
                null, // Auto-select
                organizationId,
                initiatorId
        );

        EntitySnapshot snapshot = new EntitySnapshot(
                "CONTRACT",
                null,
                entityId,
                Map.of(),
                Instant.now()
        );

        Template template = createTemplateWithStages(roleId);

        when(entityAdapter.fetchEntitySnapshot("CONTRACT", entityId))
                .thenReturn(snapshot);
        when(matchService.findMatchingTemplates(snapshot))
                .thenReturn(List.of(template));
        when(matchService.selectBestTemplate(List.of(template)))
                .thenReturn(template);
        when(roleResolverAdapter.resolveRole(eq(roleId), eq(organizationId), any()))
                .thenReturn(List.of(userId));
        when(processRepository.save(any(ProcessInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProcessInstance result = routeGenerationService.generateRoute(request);

        assertThat(result.getTemplateId()).isEqualTo(template.getId());
        verify(matchService).findMatchingTemplates(snapshot);
        verify(matchService).selectBestTemplate(List.of(template));
    }

    @Test
    void throwsWhenTemplateNotFound() {
        UUID templateId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        RouteGenerationRequest request = new RouteGenerationRequest(
                "CONTRACT",
                entityId,
                templateId,
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        EntitySnapshot snapshot = new EntitySnapshot(
                "CONTRACT",
                null,
                entityId,
                Map.of(),
                Instant.now()
        );

        when(entityAdapter.fetchEntitySnapshot("CONTRACT", entityId))
                .thenReturn(snapshot);
        when(templateRepository.findById(templateId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> routeGenerationService.generateRoute(request))
                .isInstanceOf(TemplateNotFoundException.class);
    }

    @Test
    void resolvesFixedUserSlot() {
        UUID templateId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID initiatorId = UUID.randomUUID();
        UUID fixedUserId = UUID.randomUUID();

        RouteGenerationRequest request = new RouteGenerationRequest(
                "CONTRACT",
                entityId,
                templateId,
                organizationId,
                initiatorId
        );

        EntitySnapshot snapshot = new EntitySnapshot(
                "CONTRACT",
                null,
                entityId,
                Map.of(),
                Instant.now()
        );

        Template template = createTemplateWithFixedUserSlot(fixedUserId);

        when(entityAdapter.fetchEntitySnapshot("CONTRACT", entityId))
                .thenReturn(snapshot);
        when(templateRepository.findById(templateId))
                .thenReturn(Optional.of(template));
        when(processRepository.save(any(ProcessInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProcessInstance result = routeGenerationService.generateRoute(request);

        assertThat(result.getStages().get(0).getIterations().get(0).getParticipants())
                .hasSize(1);
        assertThat(result.getStages().get(0).getIterations().get(0).getParticipants().get(0).getUserId())
                .isEqualTo(fixedUserId);
    }

    @Test
    void setsDecisionModeDefaultToAnd() {
        UUID templateId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        RouteGenerationRequest request = new RouteGenerationRequest(
                "CONTRACT",
                entityId,
                templateId,
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        EntitySnapshot snapshot = new EntitySnapshot(
                "CONTRACT",
                null,
                entityId,
                Map.of(),
                Instant.now()
        );

        // Template with decisionMode = null
        Template template = Template.builder()
                .id(UUID.randomUUID())
                .name("Test Template")
                .processType(ProcessType.STANDARD)
                .status(LifecycleStatus.PUBLISHED)
                .version(1)
                .createdAt(Instant.now())
                .createdBy(UUID.randomUUID())
                .updatedAt(Instant.now())
                .stages(new ArrayList<>())
                .build();

        StageTemplate stage = StageTemplate.builder()
                .id(UUID.randomUUID())
                .template(template)
                .orderIdx(1)
                .name("Stage 1")
                .stageType(StageType.APPROVAL)
                .duration(3)
                .executionOrder(ExecutionOrder.PARALLEL)
                .decisionMode(null) // null
                .createdAt(Instant.now())
                .actorSlots(new ArrayList<>())
                .build();

        SlotTemplate slot = SlotTemplate.builder()
                .id(UUID.randomUUID())
                .stageTemplate(stage)
                .slotType(SlotType.ACTOR)
                .orderIdx(1)
                .required(true)
                .fixedUserId(userId)
                .createdAt(Instant.now())
                .children(new ArrayList<>())
                .build();

        stage.getActorSlots().add(slot);
        template.getStages().add(stage);

        when(entityAdapter.fetchEntitySnapshot("CONTRACT", entityId))
                .thenReturn(snapshot);
        when(templateRepository.findById(templateId))
                .thenReturn(Optional.of(template));
        when(processRepository.save(any(ProcessInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProcessInstance result = routeGenerationService.generateRoute(request);

        assertThat(result.getStages().get(0).getDecisionMode()).isEqualTo(DecisionMode.AND);
    }

    private Template createTemplateWithStages(UUID roleId) {
        Template template = Template.builder()
                .id(UUID.randomUUID())
                .name("Test Template")
                .processType(ProcessType.STANDARD)
                .status(LifecycleStatus.PUBLISHED)
                .version(1)
                .createdAt(Instant.now())
                .createdBy(UUID.randomUUID())
                .updatedAt(Instant.now())
                .stages(new ArrayList<>())
                .build();

        for (int i = 1; i <= 2; i++) {
            StageTemplate stage = StageTemplate.builder()
                    .id(UUID.randomUUID())
                    .template(template)
                    .orderIdx(i)
                    .name("Stage " + i)
                    .stageType(StageType.APPROVAL)
                    .duration(3)
                    .executionOrder(ExecutionOrder.PARALLEL)
                    .decisionMode(DecisionMode.AND)
                    .createdAt(Instant.now())
                    .actorSlots(new ArrayList<>())
                    .build();

            SlotTemplate slot = SlotTemplate.builder()
                    .id(UUID.randomUUID())
                    .stageTemplate(stage)
                    .slotType(SlotType.ACTOR)
                    .orderIdx(1)
                    .required(true)
                    .roleId(roleId)
                    .createdAt(Instant.now())
                    .children(new ArrayList<>())
                    .build();

            stage.getActorSlots().add(slot);
            template.getStages().add(stage);
        }

        return template;
    }

    private Template createTemplateWithFixedUserSlot(UUID fixedUserId) {
        Template template = Template.builder()
                .id(UUID.randomUUID())
                .name("Fixed User Template")
                .processType(ProcessType.STANDARD)
                .status(LifecycleStatus.PUBLISHED)
                .version(1)
                .createdAt(Instant.now())
                .createdBy(UUID.randomUUID())
                .updatedAt(Instant.now())
                .stages(new ArrayList<>())
                .build();

        StageTemplate stage = StageTemplate.builder()
                .id(UUID.randomUUID())
                .template(template)
                .orderIdx(1)
                .name("Stage 1")
                .stageType(StageType.APPROVAL)
                .duration(3)
                .executionOrder(ExecutionOrder.PARALLEL)
                .decisionMode(DecisionMode.AND)
                .createdAt(Instant.now())
                .actorSlots(new ArrayList<>())
                .build();

        SlotTemplate slot = SlotTemplate.builder()
                .id(UUID.randomUUID())
                .stageTemplate(stage)
                .slotType(SlotType.ACTOR)
                .orderIdx(1)
                .required(true)
                .fixedUserId(fixedUserId)
                .createdAt(Instant.now())
                .children(new ArrayList<>())
                .build();

        stage.getActorSlots().add(slot);
        template.getStages().add(stage);

        return template;
    }
}
