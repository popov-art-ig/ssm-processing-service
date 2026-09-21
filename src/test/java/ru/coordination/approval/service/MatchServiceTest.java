package ru.coordination.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.coordination.approval.domain.common.DecisionMode;
import ru.coordination.approval.domain.common.ExecutionOrder;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.common.StageType;
import ru.coordination.approval.domain.template.ApplicabilityRule;
import ru.coordination.approval.domain.template.ApplicabilityRuleRepository;
import ru.coordination.approval.domain.template.StageTemplate;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;
import ru.coordination.approval.dto.EntitySnapshot;
import ru.coordination.approval.exception.NoMatchingTemplateException;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private TemplateRepository templateRepository;

    @Mock
    private ApplicabilityRuleRepository applicabilityRuleRepository;

    private MatchService matchService;

    @BeforeEach
    void setUp() {
        matchService = new MatchService(templateRepository, applicabilityRuleRepository);
    }

    @Test
    void findsTemplateMatchingEntityType() {
        Template template = createTemplate("Contract Approval");
        ApplicabilityRule rule = createRule(
                template.getId(),
                1,
                new String[]{"CONTRACT"},
                null,
                null
        );

        when(templateRepository.findByStatus(LifecycleStatus.PUBLISHED))
                .thenReturn(List.of(template));
        when(applicabilityRuleRepository.findByTemplateIdOrderByRuleIdx(template.getId()))
                .thenReturn(List.of(rule));

        EntitySnapshot snapshot = new EntitySnapshot(
                "CONTRACT",
                null,
                UUID.randomUUID(),
                Map.of(),
                Instant.now()
        );

        List<Template> matching = matchService.findMatchingTemplates(snapshot);

        assertThat(matching).hasSize(1);
        assertThat(matching.get(0).getName()).isEqualTo("Contract Approval");
    }

    @Test
    void doesNotMatchDifferentEntityType() {
        Template template = createTemplate("Contract Approval");
        ApplicabilityRule rule = createRule(
                template.getId(),
                1,
                new String[]{"CONTRACT"},
                null,
                null
        );

        when(templateRepository.findByStatus(LifecycleStatus.PUBLISHED))
                .thenReturn(List.of(template));
        when(applicabilityRuleRepository.findByTemplateIdOrderByRuleIdx(template.getId()))
                .thenReturn(List.of(rule));

        EntitySnapshot snapshot = new EntitySnapshot(
                "INVOICE",
                null,
                UUID.randomUUID(),
                Map.of(),
                Instant.now()
        );

        List<Template> matching = matchService.findMatchingTemplates(snapshot);

        assertThat(matching).isEmpty();
    }

    @Test
    void matchesEntitySubtype() {
        Template template = createTemplate("High Priority Contract");
        ApplicabilityRule rule = createRule(
                template.getId(),
                1,
                new String[]{"CONTRACT"},
                new String[]{"HIGH_PRIORITY"},
                null
        );

        when(templateRepository.findByStatus(LifecycleStatus.PUBLISHED))
                .thenReturn(List.of(template));
        when(applicabilityRuleRepository.findByTemplateIdOrderByRuleIdx(template.getId()))
                .thenReturn(List.of(rule));

        EntitySnapshot snapshot = new EntitySnapshot(
                "CONTRACT",
                "HIGH_PRIORITY",
                UUID.randomUUID(),
                Map.of(),
                Instant.now()
        );

        List<Template> matching = matchService.findMatchingTemplates(snapshot);

        assertThat(matching).hasSize(1);
    }

    @Test
    void matchesAttributeConditionEq() {
        Template template = createTemplate("Million+ Contract");
        ApplicabilityRule rule = createRule(
                template.getId(),
                1,
                new String[]{"CONTRACT"},
                null,
                Map.of("priority", Map.of("eq", "HIGH"))
        );

        when(templateRepository.findByStatus(LifecycleStatus.PUBLISHED))
                .thenReturn(List.of(template));
        when(applicabilityRuleRepository.findByTemplateIdOrderByRuleIdx(template.getId()))
                .thenReturn(List.of(rule));

        EntitySnapshot snapshot = new EntitySnapshot(
                "CONTRACT",
                null,
                UUID.randomUUID(),
                Map.of("priority", "HIGH"),
                Instant.now()
        );

        List<Template> matching = matchService.findMatchingTemplates(snapshot);

        assertThat(matching).hasSize(1);
    }

    @Test
    void matchesAttributeConditionGte() {
        Template template = createTemplate("Million+ Contract");
        ApplicabilityRule rule = createRule(
                template.getId(),
                1,
                new String[]{"CONTRACT"},
                null,
                Map.of("amount", Map.of("gte", 1000000))
        );

        when(templateRepository.findByStatus(LifecycleStatus.PUBLISHED))
                .thenReturn(List.of(template));
        when(applicabilityRuleRepository.findByTemplateIdOrderByRuleIdx(template.getId()))
                .thenReturn(List.of(rule));

        EntitySnapshot snapshot = new EntitySnapshot(
                "CONTRACT",
                null,
                UUID.randomUUID(),
                Map.of("amount", 1500000),
                Instant.now()
        );

        List<Template> matching = matchService.findMatchingTemplates(snapshot);

        assertThat(matching).hasSize(1);
    }

    @Test
    void sortsTemplatesByPriority() {
        Template template1 = createTemplate("Generic Contract");
        ApplicabilityRule rule1 = createRule(
                template1.getId(),
                10,
                new String[]{"CONTRACT"},
                null,
                null
        );

        Template template2 = createTemplate("High Priority Contract");
        ApplicabilityRule rule2 = createRule(
                template2.getId(),
                1,
                new String[]{"CONTRACT"},
                null,
                Map.of("priority", Map.of("eq", "HIGH"))
        );

        when(templateRepository.findByStatus(LifecycleStatus.PUBLISHED))
                .thenReturn(List.of(template1, template2));
        when(applicabilityRuleRepository.findByTemplateIdOrderByRuleIdx(template1.getId()))
                .thenReturn(List.of(rule1));
        when(applicabilityRuleRepository.findByTemplateIdOrderByRuleIdx(template2.getId()))
                .thenReturn(List.of(rule2));

        EntitySnapshot snapshot = new EntitySnapshot(
                "CONTRACT",
                null,
                UUID.randomUUID(),
                Map.of("priority", "HIGH"),
                Instant.now()
        );

        List<Template> matching = matchService.findMatchingTemplates(snapshot);

        assertThat(matching).hasSize(2);
        assertThat(matching.get(0).getName()).isEqualTo("High Priority Contract");
        assertThat(matching.get(1).getName()).isEqualTo("Generic Contract");
    }

    @Test
    void universalTemplateMatchesEverything() {
        Template template = createTemplate("Universal Template");

        when(templateRepository.findByStatus(LifecycleStatus.PUBLISHED))
                .thenReturn(List.of(template));
        when(applicabilityRuleRepository.findByTemplateIdOrderByRuleIdx(template.getId()))
                .thenReturn(List.of()); // No rules = universal

        EntitySnapshot snapshot = new EntitySnapshot(
                "ANY_TYPE",
                null,
                UUID.randomUUID(),
                Map.of(),
                Instant.now()
        );

        List<Template> matching = matchService.findMatchingTemplates(snapshot);

        assertThat(matching).hasSize(1);
    }

    @Test
    void selectBestTemplateReturnsFirst() {
        Template template1 = createTemplate("Template 1");
        Template template2 = createTemplate("Template 2");

        Template best = matchService.selectBestTemplate(List.of(template1, template2));

        assertThat(best).isEqualTo(template1);
    }

    @Test
    void selectBestTemplateThrowsWhenNoTemplates() {
        assertThatThrownBy(() -> matchService.selectBestTemplate(List.of()))
                .isInstanceOf(NoMatchingTemplateException.class);
    }

    private Template createTemplate(String name) {
        return Template.builder()
                .id(UUID.randomUUID())
                .name(name)
                .processType(ProcessType.STANDARD)
                .status(LifecycleStatus.PUBLISHED)
                .version(1)
                .createdAt(Instant.now())
                .createdBy(UUID.randomUUID())
                .updatedAt(Instant.now())
                .stages(new ArrayList<>())
                .build();
    }

    private ApplicabilityRule createRule(
            UUID templateId,
            int ruleIdx,
            String[] entityTypes,
            String[] entitySubtypes,
            Map<String, Object> attributeConditions
    ) {
        return ApplicabilityRule.builder()
                .id(UUID.randomUUID())
                .templateId(templateId)
                .ruleIdx(ruleIdx)
                .entityTypes(entityTypes)
                .entitySubtypes(entitySubtypes)
                .attributeConditions(attributeConditions)
                .createdAt(Instant.now())
                .build();
    }
}
