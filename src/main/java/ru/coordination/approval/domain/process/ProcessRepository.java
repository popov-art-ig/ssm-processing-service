package ru.coordination.approval.domain.process;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Доступ к {@link ProcessInstance} — репозиторий агрегата, а не внутренний инструмент одного
 * сервиса (PHASE-03). {@link StageInstance}/{@link StageIteration}/{@link Participant} не
 * имеют отдельных репозиториев — сохраняются каскадом через {@code ProcessInstance.stages}
 * (см. {@code doc/tasks/PHASE-03-tasks.md} T3).
 */
public interface ProcessRepository extends JpaRepository<ProcessInstance, UUID> {
}
