package ru.coordination.approval.domain.process;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Доступ к {@link StageInstance} напрямую (не только через каскад
 * {@code ProcessInstance.stages}) — нужен там, где известен id конкретного этапа
 * (PHASE-04, например {@code ProcessServiceIntegrationTest}), без ленивой навигации по
 * detached {@link ProcessInstance}.
 */
public interface StageRepository extends JpaRepository<StageInstance, UUID> {
}
