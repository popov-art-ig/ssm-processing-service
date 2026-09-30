package ru.coordination.approval.api.mapper;

import org.springframework.stereotype.Component;
import ru.coordination.approval.api.dto.comment.CommentDto;
import ru.coordination.approval.domain.process.Comment;

@Component
public class CommentMapper {

    public CommentDto toDto(Comment comment) {
        return new CommentDto(
                comment.getId(),
                comment.getProcess().getId(),
                comment.getStage() != null ? comment.getStage().getId() : null,
                null, // Comment doesn't have a remark relation
                comment.getText(),
                comment.getAuthorId(),
                comment.getCreatedAt()
        );
    }
}
