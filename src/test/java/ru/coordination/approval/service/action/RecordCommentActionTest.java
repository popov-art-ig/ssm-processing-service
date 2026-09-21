package ru.coordination.approval.service.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.process.AuthorRole;
import ru.coordination.approval.domain.process.Comment;
import ru.coordination.approval.domain.process.CommentRepository;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.ParticipantRole;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.engine.TransitionContext;

@ExtendWith(MockitoExtension.class)
class RecordCommentActionTest {

    @Mock
    private CommentRepository commentRepository;

    @InjectMocks
    private RecordCommentAction action;

    @Test
    void savesCommentWithCorrectFields() {
        UUID actorId = UUID.randomUUID();

        ProcessInstance process = ProcessInstance.builder()
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        StageInstance stage = StageInstance.builder()
                .process(process)
                .createdAt(Instant.now())
                .build();

        StageIteration iteration = StageIteration.builder()
                .stage(stage)
                .createdAt(Instant.now())
                .build();

        Participant participant = Participant.builder()
                .stageIteration(iteration)
                .userId(actorId)
                .status("Assigned")
                .role(ParticipantRole.APPROVER)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Map<String, Object> parameters = Map.of("comment", "This needs revision");

        TransitionContext context = new TransitionContext(participant, actorId, ActorType.USER, parameters);

        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        action.execute(context);

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());

        Comment saved = captor.getValue();
        assertThat(saved.getProcess()).isEqualTo(process);
        assertThat(saved.getStage()).isEqualTo(stage);
        assertThat(saved.getStageIteration()).isEqualTo(iteration);
        assertThat(saved.getParticipant()).isEqualTo(participant);
        assertThat(saved.getAuthorId()).isEqualTo(actorId);
        assertThat(saved.getAuthorRole()).isEqualTo(AuthorRole.PARTICIPANT);
        assertThat(saved.getText()).isEqualTo("This needs revision");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void doesNotSaveCommentWhenTextIsNull() {
        UUID actorId = UUID.randomUUID();
        Participant participant = Participant.builder()
                .userId(actorId)
                .status("Assigned")
                .role(ParticipantRole.APPROVER)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Map<String, Object> parameters = Map.of("decisionType", "APPROVE");

        TransitionContext context = new TransitionContext(participant, actorId, ActorType.USER, parameters);

        action.execute(context);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void doesNotSaveCommentWhenTextIsBlank() {
        UUID actorId = UUID.randomUUID();
        Participant participant = Participant.builder()
                .userId(actorId)
                .status("Assigned")
                .role(ParticipantRole.APPROVER)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Map<String, Object> parameters = Map.of("comment", "   ");

        TransitionContext context = new TransitionContext(participant, actorId, ActorType.USER, parameters);

        action.execute(context);

        verify(commentRepository, never()).save(any());
    }
}
