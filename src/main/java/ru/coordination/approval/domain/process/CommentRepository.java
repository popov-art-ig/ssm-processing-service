package ru.coordination.approval.domain.process;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Доступ к {@link Comment} — комментариям процесса, которые участники оставляют при решениях
 * (в том числе при {@code ApproveWithComments}, PHASE-05). Отдельный репозиторий нужен
 * read-model'ям: {@code ApprovalViewService} собирает комментарии процесса одним запросом.
 */
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /** Все комментарии процесса (в порядке вставки) — независимо от этапа и участника. */
    List<Comment> findByProcessId(UUID processId);

    /** Комментарии конкретного этапа процесса — для карточки этапа в UI. */
    List<Comment> findByProcessIdAndStageId(UUID processId, UUID stageId);
}
