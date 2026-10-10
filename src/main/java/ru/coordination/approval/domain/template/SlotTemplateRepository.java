package ru.coordination.approval.domain.template;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Доступ к {@link SlotTemplate} — слотам участников шаблона. Метод возвращает все слоты этапа
 * одним запросом; фильтрация по {@link SlotType} выполняется вызывающим кодом (например
 * {@code ValidateTemplateStructureAction} проверяет инварианты связей по типу слота).
 */
public interface SlotTemplateRepository extends JpaRepository<SlotTemplate, UUID> {

    /**
     * Слоты этапа. Возвращаются все строки с этим {@code stage_template_id} — то есть только
     * {@link SlotType#ACTOR} (у вложенных доп. согласующих поле пустое по check-констрейнту).
     */
    List<SlotTemplate> findByStageTemplateId(UUID stageTemplateId);
}
