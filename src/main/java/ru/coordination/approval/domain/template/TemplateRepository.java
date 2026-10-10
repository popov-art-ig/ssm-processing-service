package ru.coordination.approval.domain.template;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.coordination.approval.domain.common.LifecycleStatus;

/**
 * Доступ к {@link Template} — репозиторий агрегата TemplateAggregate (PHASE-14), включая
 * {@code Specification}-запросы для фильтрации списка шаблонов в REST API.
 *
 * <p>Этапы и правила применимости отдельного «сохранения» не требуют: они принадлежат версии
 * шаблона и сохраняются каскадом ({@code cascade = ALL, orphanRemoval = true}), поэтому
 * {@code stage_template}/{@code applicability_rule} изменяются только вместе с новой строкой
 * {@code Template} (ADR-023).
 */
public interface TemplateRepository
        extends JpaRepository<Template, UUID>, JpaSpecificationExecutor<Template> {

    /**
     * Точное совпадение по имени и статусу (например «есть ли уже DRAFT с таким именем»).
     * Поиск по подстроке имени в REST API выполняется не здесь, а через {@code Specification}.
     */
    List<Template> findByNameAndStatus(String name, LifecycleStatus status);

    /** Все шаблоны указанного статуса, новые версии первыми: так {@code MatchService} получает пул кандидатов. */
    @Query("SELECT t FROM Template t WHERE t.status = :status ORDER BY t.version DESC")
    List<Template> findByStatus(@Param("status") LifecycleStatus status);

    /**
     * Непосредственные потомки версии по {@link Template#getParentTemplateId()} — одна ступень
     * цепочки, а не вся цепочка версий. Текущий код метод не использует.
     */
    @Query("SELECT t FROM Template t WHERE t.parentTemplateId = :parentId ORDER BY t.version DESC")
    List<Template> findChildVersions(@Param("parentId") UUID parentId);
}
