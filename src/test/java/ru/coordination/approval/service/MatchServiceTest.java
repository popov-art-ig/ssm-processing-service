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
import ru.coordination.approval.dto.generation.EntitySnapshot;
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
                template,
                1,
                List.of("CONTRACT"),
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
                template,
                1,
                List.of("CONTRACT"),
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
                template,
                1,
                List.of("CONTRACT"),
                List.of("HIGH_PRIORITY"),
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
                template,
                1,
                List.of("CONTRACT"),
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
                template,
                1,
                List.of("CONTRACT"),
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
                template1,
                10,
                List.of("CONTRACT"),
                null,
                null
        );

        Template template2 = createTemplate("High Priority Contract");
        ApplicabilityRule rule2 = createRule(
                template2,
                1,
                List.of("CONTRACT"),
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

    /**
     * При равном приоритете (одинаковый {@code ruleIdx}) выбирается шаблон с большей версией.
     *
     * <p>Регрессия: сортировка была только по {@code ruleIdx}, поэтому при равном приоритете
     * выбирался произвольный шаблон — на практике самый старый, и новые шаблоны не
     * использовались вовсе (найдено при рантайм-проверке сквозного сценария).
     */
    @Test
    void prefersHigherVersionOnEqualPriority() {
        Template older = createTemplateWithVersion("Template v1", 1, Instant.now().minusSeconds(600));
        Template newer = createTemplateWithVersion("Template v3", 3, Instant.now());

        when(templateRepository.findByStatus(LifecycleStatus.PUBLISHED))
                .thenReturn(List.of(older, newer));
        when(applicabilityRuleRepository.findByTemplateIdOrderByRuleIdx(older.getId()))
                .thenReturn(List.of(createRule(older, 1, List.of("CONTRACT"), null, null)));
        when(applicabilityRuleRepository.findByTemplateIdOrderByRuleIdx(newer.getId()))
                .thenReturn(List.of(createRule(newer, 1, List.of("CONTRACT"), null, null)));

        List<Template> matching = matchService.findMatchingTemplates(snapshotOf("CONTRACT"));

        assertThat(matching).hasSize(2);
        assertThat(matching.get(0)).isEqualTo(newer);
        assertThat(matchService.selectBestTemplate(matching)).isEqualTo(newer);
    }

    @Test
    void prefersNewestCreatedAtWhenVersionAndPriorityAreEqual() {
        Template older = createTemplateWithVersion("Old", 1, Instant.now().minusSeconds(600));
        Template newer = createTemplateWithVersion("New", 1, Instant.now());

        when(templateRepository.findByStatus(LifecycleStatus.PUBLISHED))
                .thenReturn(List.of(older, newer));
        when(applicabilityRuleRepository.findByTemplateIdOrderByRuleIdx(older.getId()))
                .thenReturn(List.of(createRule(older, 1, List.of("CONTRACT"), null, null)));
        when(applicabilityRuleRepository.findByTemplateIdOrderByRuleIdx(newer.getId()))
                .thenReturn(List.of(createRule(newer, 1, List.of("CONTRACT"), null, null)));

        List<Template> matching = matchService.findMatchingTemplates(snapshotOf("CONTRACT"));

        assertThat(matching.get(0)).isEqualTo(newer);
    }

    @Test
    void lowerRuleIdxStillWinsOverHigherVersion() {
        Template specific = createTemplateWithVersion("Specific", 1, Instant.now().minusSeconds(600));
        Template generic = createTemplateWithVersion("Generic", 5, Instant.now());

        when(templateRepository.findByStatus(LifecycleStatus.PUBLISHED))
                .thenReturn(List.of(specific, generic));
        when(applicabilityRuleRepository.findByTemplateIdOrderByRuleIdx(specific.getId()))
                .thenReturn(List.of(createRule(specific, 1, List.of("CONTRACT"), null, null)));
        when(applicabilityRuleRepository.findByTemplateIdOrderByRuleIdx(generic.getId()))
                .thenReturn(List.of(createRule(generic, 3, List.of("CONTRACT"), null, null)));

        List<Template> matching = matchService.findMatchingTemplates(snapshotOf("CONTRACT"));

        assertThat(matching.get(0)).as("приоритет правила важнее версии").isEqualTo(specific);
    }

    /**
     * Если в цепочке версий есть более новая опубликованная версия, старая кандидатом быть не
     * должна (ADR-023: версия — отдельная строка с {@code parentTemplateId}).
     */
    @Test
    void excludesDeprecatedByNewerPublishedVersionInChain() {
        Template v1 = createTemplateWithVersion("Chain v1", 1, Instant.now().minusSeconds(600));
        Template v2 = createTemplateWithVersion("Chain v2", 2, Instant.now());
        v2.setParentTemplateId(v1.getId());

        when(templateRepository.findByStatus(LifecycleStatus.PUBLISHED))
                .thenReturn(List.of(v1, v2));
        // v1 исключается ДО обращения к правилам применимости (в цепочке есть v2 с большей
        // версией), поэтому заглушка нужна только для v2 — иначе Mockito ругается на
        // неиспользованную заглушку, что здесь как раз подтверждает ранний выход.
        when(applicabilityRuleRepository.findByTemplateIdOrderByRuleIdx(v2.getId()))
                .thenReturn(List.of(createRule(v2, 1, List.of("CONTRACT"), null, null)));

        List<Template> matching = matchService.findMatchingTemplates(snapshotOf("CONTRACT"));

        assertThat(matching).containsExactly(v2);
    }

    private EntitySnapshot snapshotOf(String entityType) {
        return new EntitySnapshot(entityType, null, UUID.randomUUID(), Map.of(), Instant.now());
    }

    private Template createTemplateWithVersion(String name, int version, Instant createdAt) {
        return Template.builder()
                .id(UUID.randomUUID())
                .name(name)
                .processType(ProcessType.STANDARD)
                .status(LifecycleStatus.PUBLISHED)
                .version(version)
                .createdAt(createdAt)
                .createdBy(UUID.randomUUID())
                .updatedAt(createdAt)
                .stages(new ArrayList<>())
                .build();
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
            Template template,
            int ruleIdx,
            List<String> entityTypes,
            List<String> entitySubtypes,
            Map<String, Object> attributeConditions
    ) {
        return ApplicabilityRule.builder()
                .id(UUID.randomUUID())
                .template(template)
                .ruleIdx(ruleIdx)
                .entityTypes(entityTypes != null ? entityTypes : List.of())
                .entitySubtypes(entitySubtypes != null ? entitySubtypes : List.of())
                .attributeConditions(attributeConditions != null ? attributeConditions : Map.of())
                .createdAt(Instant.now())
                .build();
    }
}
