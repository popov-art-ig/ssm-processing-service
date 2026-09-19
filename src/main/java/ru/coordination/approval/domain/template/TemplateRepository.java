package ru.coordination.approval.domain.template;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Доступ к {@link Template}. Полноценный флоу создания/публикации шаблонов — вне объёма
 * PHASE-03 ({@code MatchService}/{@code RouteGeneratorService} не входят); репозиторий нужен
 * здесь только для того, чтобы тесты {@code ProcessService} могли создать валидный
 * {@code template_ref} для {@code ProcessInstance} (FK {@code process_instance.template_ref}).
 */
public interface TemplateRepository extends JpaRepository<Template, UUID> {
}
