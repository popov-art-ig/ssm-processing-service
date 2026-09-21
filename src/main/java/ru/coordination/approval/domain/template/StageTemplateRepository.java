package ru.coordination.approval.domain.template;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StageTemplateRepository extends JpaRepository<StageTemplate, UUID> {
    List<StageTemplate> findByTemplateIdOrderByOrderIdx(UUID templateId);
}
