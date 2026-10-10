package ru.coordination.approval.domain.template;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Доступ к {@link StageTemplate} конкретной версии шаблона. Отдельный репозиторий нужен потому,
 * что этапы — самостоятельные сущности агрегата (не {@code @Embeddable}): их читают без загрузки
 * всего {@link Template} и без ленивой навигации по коллекции.
 */
public interface StageTemplateRepository extends JpaRepository<StageTemplate, UUID> {

    /** Этапы шаблона в порядке маршрута ({@code orderIdx} по возрастанию). */
    List<StageTemplate> findByTemplateIdOrderByOrderIdx(UUID templateId);
}
