package ru.coordination.approval.domain.template;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Доступ к {@link ApplicabilityRule} — правилам применимости конкретной версии шаблона.
 * Используется подбором шаблона ({@code MatchService}): порядок возврата значим, так как
 * приоритет шаблона определяется первым подошедшим правилом.
 */
public interface ApplicabilityRuleRepository extends JpaRepository<ApplicabilityRule, UUID> {

    /** Правила шаблона в порядке старшинства ({@code ruleIdx} по возрастанию). */
    List<ApplicabilityRule> findByTemplateIdOrderByRuleIdx(UUID templateId);
}
