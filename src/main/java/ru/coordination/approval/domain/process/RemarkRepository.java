package ru.coordination.approval.domain.process;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RemarkRepository extends JpaRepository<Remark, UUID> {
    boolean existsByProcessIdAndStatusIn(UUID processId, List<String> statuses);

    List<Remark> findByProcessId(UUID processId);

    List<Remark> findByProcessIdAndStatus(UUID processId, String status);
}
