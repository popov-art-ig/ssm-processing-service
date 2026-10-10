package ru.coordination.approval.service.action;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.Comment;
import ru.coordination.approval.domain.process.CommentRepository;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.AuthorRole;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

@Component("recordCommentAction")
@RequiredArgsConstructor
public class RecordCommentAction implements Action {

    private final CommentRepository commentRepository;

    @Override
    public void execute(TransitionContext context) {
        Participant participant = (Participant) context.entity();
        String commentText = (String) context.parameters().get("comment");

        if (commentText == null || commentText.isBlank()) {
            return;
        }

        Comment comment = Comment.builder()
                // BaseEntity объявляет @Id без @GeneratedValue — id задаёт приложение,
                // иначе Hibernate падает с "Identifier of entity ... must be manually assigned".
                .id(UUID.randomUUID())
                .process(participant.getStageIteration().getStage().getProcess())
                .stage(participant.getStageIteration().getStage())
                .stageIteration(participant.getStageIteration())
                .participant(participant)
                .authorId(context.actorId())
                // author_role объявлен NOT NULL в схеме (V8__remarks_and_comments.sql, строка 40)
                .authorRole(AuthorRole.APPROVER)
                .text(commentText)
                .createdAt(Instant.now())
                .build();

        commentRepository.save(comment);
    }
}
