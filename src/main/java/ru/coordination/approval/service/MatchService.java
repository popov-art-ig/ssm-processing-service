package ru.coordination.approval.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.coordination.approval.domain.common.LifecycleStatus;
import ru.coordination.approval.domain.template.ApplicabilityRule;
import ru.coordination.approval.domain.template.ApplicabilityRuleRepository;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;
import ru.coordination.approval.dto.EntitySnapshot;
import ru.coordination.approval.exception.NoMatchingTemplateException;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final TemplateRepository templateRepository;
    private final ApplicabilityRuleRepository applicabilityRuleRepository;

    public List<Template> findMatchingTemplates(EntitySnapshot entitySnapshot) {
        List<Template> allPublished = templateRepository.findByStatus(LifecycleStatus.PUBLISHED);
        List<TemplateMatch> matches = new ArrayList<>();

        for (Template template : allPublished) {
            List<ApplicabilityRule> rules = applicabilityRuleRepository
                    .findByTemplateIdOrderByRuleIdx(template.getId());

            if (rules.isEmpty()) {
                // Universal template - matches everything
                matches.add(new TemplateMatch(template, Integer.MAX_VALUE));
                continue;
            }

            for (ApplicabilityRule rule : rules) {
                if (ruleMatches(rule, entitySnapshot)) {
                    matches.add(new TemplateMatch(template, rule.getRuleIdx()));
                    break; // First matching rule wins
                }
            }
        }

        // Sort by priority (lower ruleIdx = higher priority)
        matches.sort(Comparator.comparingInt(TemplateMatch::priority));

        return matches.stream()
                .map(TemplateMatch::template)
                .toList();
    }

    public Template selectBestTemplate(List<Template> matchingTemplates) {
        if (matchingTemplates.isEmpty()) {
            throw new NoMatchingTemplateException("No templates match the entity");
        }
        return matchingTemplates.get(0);
    }

    private boolean ruleMatches(ApplicabilityRule rule, EntitySnapshot snapshot) {
        // Check entity type
        if (rule.getEntityTypes() != null && rule.getEntityTypes().length > 0) {
            boolean typeMatches = false;
            for (String type : rule.getEntityTypes()) {
                if (type.equals(snapshot.entityType())) {
                    typeMatches = true;
                    break;
                }
            }
            if (!typeMatches) {
                return false;
            }
        }

        // Check entity subtype
        if (rule.getEntitySubtypes() != null && rule.getEntitySubtypes().length > 0) {
            if (snapshot.entitySubtype() == null) {
                return false;
            }
            boolean subtypeMatches = false;
            for (String subtype : rule.getEntitySubtypes()) {
                if (subtype.equals(snapshot.entitySubtype())) {
                    subtypeMatches = true;
                    break;
                }
            }
            if (!subtypeMatches) {
                return false;
            }
        }

        // Check attribute conditions
        if (rule.getAttributeConditions() != null && !rule.getAttributeConditions().isEmpty()) {
            for (Map.Entry<String, Object> entry : rule.getAttributeConditions().entrySet()) {
                String attrName = entry.getKey();
                Object condition = entry.getValue();

                if (!attributeMatches(attrName, condition, snapshot.attributes())) {
                    return false;
                }
            }
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean attributeMatches(
            String attrName,
            Object condition,
            Map<String, Object> attributes
    ) {
        Object attrValue = attributes.get(attrName);
        if (attrValue == null) {
            return false;
        }

        if (!(condition instanceof Map)) {
            return false;
        }

        Map<String, Object> conditionMap = (Map<String, Object>) condition;

        // eq operator
        if (conditionMap.containsKey("eq")) {
            return attrValue.equals(conditionMap.get("eq"));
        }

        // gte operator (greater than or equal)
        if (conditionMap.containsKey("gte")) {
            Object threshold = conditionMap.get("gte");
            if (attrValue instanceof Number && threshold instanceof Number) {
                double value = ((Number) attrValue).doubleValue();
                double min = ((Number) threshold).doubleValue();
                return value >= min;
            }
        }

        // lte operator (less than or equal)
        if (conditionMap.containsKey("lte")) {
            Object threshold = conditionMap.get("lte");
            if (attrValue instanceof Number && threshold instanceof Number) {
                double value = ((Number) attrValue).doubleValue();
                double max = ((Number) threshold).doubleValue();
                return value <= max;
            }
        }

        // in operator
        if (conditionMap.containsKey("in")) {
            Object inValues = conditionMap.get("in");
            if (inValues instanceof List) {
                return ((List<?>) inValues).contains(attrValue);
            }
        }

        return false;
    }

    private record TemplateMatch(Template template, int priority) {
    }
}
