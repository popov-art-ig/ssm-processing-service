package ru.coordination.approval.service.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.engine.TransitionContext;

class CompleteProcessActionTest {

    private CompleteProcessAction action;

    @BeforeEach
    void setUp() {
        action = new CompleteProcessAction();
    }

    @Test
    void setsCompletedAtTimestamp() {
        Instant beforeExecution = Instant.now();

        ProcessInstance process = ProcessInstance.builder()
                .id(UUID.randomUUID())
                .processType(ProcessType.STANDARD)
                .configVersion(1)
                .status("InProgress")
                .initiatorId(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();

        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        action.execute(context);

        Instant afterExecution = Instant.now();

        assertThat(process.getCompletedAt()).isNotNull();
        assertThat(process.getCompletedAt()).isBetween(beforeExecution, afterExecution);
    }

    @Test
    void overwritesExistingCompletedAt() {
        Instant oldCompletedAt = Instant.parse("2026-01-01T00:00:00Z");

        ProcessInstance process = ProcessInstance.builder()
                .id(UUID.randomUUID())
                .processType(ProcessType.STANDARD)
                .configVersion(1)
                .status("InProgress")
                .initiatorId(UUID.randomUUID())
                .createdAt(Instant.now())
                .completedAt(oldCompletedAt)
                .build();

        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        action.execute(context);

        assertThat(process.getCompletedAt()).isNotNull();
        assertThat(process.getCompletedAt()).isNotEqualTo(oldCompletedAt);
        assertThat(process.getCompletedAt()).isAfter(oldCompletedAt);
    }
}
