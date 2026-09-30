package ru.coordination.approval.domain.process;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    List<Comment> findByProcessId(UUID processId);

    List<Comment> findByProcessIdAndStageId(UUID processId, UUID stageId);
}
