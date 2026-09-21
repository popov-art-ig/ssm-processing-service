package ru.coordination.approval.service.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.engine.TransitionContext;

class IsParticipantGuardTest {

    private final IsParticipantGuard guard = new IsParticipantGuard();

    @Test
    void alwaysReturnsTrue() {
        TransitionContext context = new TransitionContext("any entity", UUID.randomUUID(), ActorType.USER, Map.of());

        assertThat(guard.evaluate(context)).isTrue();
    }
}
