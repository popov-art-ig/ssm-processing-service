package ru.coordination.approval.service.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.engine.TransitionContext;

class IsInitiatorGuardTest {

    private final IsInitiatorGuard guard = new IsInitiatorGuard();

    @Test
    void passesWhenActorIsInitiator() {
        UUID initiatorId = UUID.randomUUID();
        ProcessInstance process = ProcessInstance.builder().initiatorId(initiatorId).build();

        boolean result = guard.evaluate(new TransitionContext(process, initiatorId, ActorType.USER, Map.of()));

        assertThat(result).isTrue();
    }

    @Test
    void failsWhenActorIsNotInitiator() {
        ProcessInstance process = ProcessInstance.builder().initiatorId(UUID.randomUUID()).build();

        boolean result = guard.evaluate(
                new TransitionContext(process, UUID.randomUUID(), ActorType.USER, Map.of()));

        assertThat(result).isFalse();
    }

    @Test
    void failsWhenActorIdIsNull() {
        ProcessInstance process = ProcessInstance.builder().initiatorId(UUID.randomUUID()).build();

        boolean result = guard.evaluate(new TransitionContext(process, null, ActorType.SYSTEM, Map.of()));

        assertThat(result).isFalse();
    }
}
