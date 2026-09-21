package ru.coordination.approval.service.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import ru.coordination.approval.domain.process.Decision;
import ru.coordination.approval.domain.process.DecisionRepository;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.ParticipantRole;
import ru.coordination.approval.engine.TransitionContext;

@ExtendWith(MockitoExtension.class)
class RecordDecisionActionTest {

    @Mock
    private DecisionRepository decisionRepository;

    @InjectMocks
    private RecordDecisionAction action;

    @Test
    void savesDecisionWithCorrectFields() {
        UUID actorId = UUID.randomUUID();
        Participant participant = Participant.builder()
                .userId(actorId)
                .status("Assigned")
                .role(ParticipantRole.APPROVER)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Map<String, Object> parameters = Map.of(
                "decisionType", "APPROVE",
                "comment", "Looks good");

        TransitionContext context = new TransitionContext(participant, actorId, ActorType.USER, parameters);

        when(decisionRepository.save(any(Decision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        action.execute(context);

        ArgumentCaptor<Decision> captor = ArgumentCaptor.forClass(Decision.class);
        verify(decisionRepository).save(captor.capture());

        Decision saved = captor.getValue();
        assertThat(saved.getParticipant()).isEqualTo(participant);
        assertThat(saved.getResult()).isEqualTo("APPROVE");
        assertThat(saved.getComment()).isEqualTo("Looks good");
        assertThat(saved.isAuto()).isFalse();
        assertThat(saved.getRecordedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void savesDecisionWithNullComment() {
        UUID actorId = UUID.randomUUID();
        Participant participant = Participant.builder()
                .userId(actorId)
                .status("Assigned")
                .role(ParticipantRole.APPROVER)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Map<String, Object> parameters = Map.of("decisionType", "REJECT");

        TransitionContext context = new TransitionContext(participant, actorId, ActorType.USER, parameters);

        when(decisionRepository.save(any(Decision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        action.execute(context);

        ArgumentCaptor<Decision> captor = ArgumentCaptor.forClass(Decision.class);
        verify(decisionRepository).save(captor.capture());

        Decision saved = captor.getValue();
        assertThat(saved.getComment()).isNull();
    }
}
