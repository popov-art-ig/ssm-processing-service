package ru.coordination.approval.domain.template;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.coordination.approval.domain.common.LifecycleStatus;

public interface TemplateRepository extends JpaRepository<Template, UUID> {
    List<Template> findByNameAndStatus(String name, LifecycleStatus status);

    @Query("SELECT t FROM Template t WHERE t.status = :status ORDER BY t.version DESC")
    List<Template> findByStatus(@Param("status") LifecycleStatus status);

    @Query("SELECT t FROM Template t WHERE t.parentTemplateId = :parentId ORDER BY t.version DESC")
    List<Template> findChildVersions(@Param("parentId") UUID parentId);
}
