package ru.coordination.approval.domain.template;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicabilityRuleRepository extends JpaRepository<ApplicabilityRule, UUID> {
    List<ApplicabilityRule> findByTemplateIdOrderByRuleIdx(UUID templateId);
}
