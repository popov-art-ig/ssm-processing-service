package ru.coordination.approval.service.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.ParticipantRole;
import ru.coordination.approval.engine.TransitionContext;

class IsAssignedParticipantGuardTest {

    private final IsAssignedParticipantGuard guard = new IsAssignedParticipantGuard();

    @Test
    void returnsTrueWhenActorIsAssignedParticipant() {
        UUID userId = UUID.randomUUID();
        Participant participant = Participant.builder()
                .userId(userId)
                .status("Assigned")
                .role(ParticipantRole.APPROVER)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        TransitionContext context = new TransitionContext(participant, userId, ActorType.USER, Map.of());

        assertThat(guard.execute(context)).isTrue();
    }

    @Test
    void returnsFalseWhenActorIdDoesNotMatch() {
        UUID userId = UUID.randomUUID();
        UUID differentUserId = UUID.randomUUID();
        Participant participant = Participant.builder()
                .userId(userId)
                .status("Assigned")
                .role(ParticipantRole.APPROVER)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        TransitionContext context = new TransitionContext(participant, differentUserId, ActorType.USER, Map.of());

        assertThat(guard.execute(context)).isFalse();
    }

    @Test
    void returnsFalseWhenStatusIsNotAssigned() {
        UUID userId = UUID.randomUUID();
        Participant participant = Participant.builder()
                .userId(userId)
                .status("Pending")
                .role(ParticipantRole.APPROVER)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        TransitionContext context = new TransitionContext(participant, userId, ActorType.USER, Map.of());

        assertThat(guard.execute(context)).isFalse();
    }

    @Test
    void returnsFalseWhenActorIdIsNull() {
        UUID userId = UUID.randomUUID();
        Participant participant = Participant.builder()
                .userId(userId)
                .status("Assigned")
                .role(ParticipantRole.APPROVER)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        TransitionContext context = new TransitionContext(participant, null, ActorType.SYSTEM, Map.of());

        assertThat(guard.execute(context)).isFalse();
    }

    @Test
    void returnsFalseWhenEntityIsNotParticipant() {
        UUID userId = UUID.randomUUID();
        String notAParticipant = "some string";

        TransitionContext context = new TransitionContext(notAParticipant, userId, ActorType.USER, Map.of());

        assertThat(guard.execute(context)).isFalse();
    }
}
