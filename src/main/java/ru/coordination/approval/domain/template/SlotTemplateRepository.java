package ru.coordination.approval.domain.template;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlotTemplateRepository extends JpaRepository<SlotTemplate, UUID> {
    List<SlotTemplate> findByStageTemplateId(UUID stageTemplateId);
}
