package ru.coordination.approval.api.mapper;

import org.springframework.stereotype.Component;
import ru.coordination.approval.api.dto.comment.CommentDto;
import ru.coordination.approval.domain.process.Comment;

/**
 * Преобразование {@code Comment} в {@link CommentDto}.
 *
 * <p>Маппер обращается к ленивым связям {@code process} и {@code stage}, поэтому вызывается внутри
 * открытой сессии. Что теряется: {@code remarkId} не заполняется никогда — связи «комментарий →
 * замечание» в модели нет, хотя контракт §18 её описывает; вложения
 * ({@code Comment.attachments}) и признак ответа ({@code parent}) в DTO тоже не выведены.
 */
@Component
public class CommentMapper {

    /**
     * Представление комментария. Обращается к ленивым связям {@code process} и {@code stage} —
     * только внутри открытой сессии; {@code remarkId} в ответе всегда {@code null} (см. описание
     * класса).
     */
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
