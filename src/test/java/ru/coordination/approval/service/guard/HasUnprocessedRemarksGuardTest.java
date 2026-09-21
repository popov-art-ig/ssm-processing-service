package ru.coordination.approval.service.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.RemarkRepository;
import ru.coordination.approval.engine.TransitionContext;

@ExtendWith(MockitoExtension.class)
class HasUnprocessedRemarksGuardTest {

    @Mock
    private RemarkRepository remarkRepository;

    @InjectMocks
    private HasUnprocessedRemarksGuard guard;

    @Test
    void returnsTrueWhenUnprocessedRemarksExist() {
        UUID processId = UUID.randomUUID();

        ProcessInstance process = ProcessInstance.builder()
                .id(processId)
                .createdAt(Instant.now())
                .build();

        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.USER, Map.of());

        when(remarkRepository.existsByProcessIdAndStatusIn(processId, List.of("Open", "InProgress")))
                .thenReturn(true);

        assertThat(guard.evaluate(context)).isTrue();
    }

    @Test
    void returnsFalseWhenNoUnprocessedRemarks() {
        UUID processId = UUID.randomUUID();

        ProcessInstance process = ProcessInstance.builder()
                .id(processId)
                .createdAt(Instant.now())
                .build();

        TransitionContext context = new TransitionContext(process, UUID.randomUUID(), ActorType.USER, Map.of());

        when(remarkRepository.existsByProcessIdAndStatusIn(processId, List.of("Open", "InProgress")))
                .thenReturn(false);

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseWhenEntityIsNotProcessInstance() {
        String notAProcess = "some string";
        TransitionContext context = new TransitionContext(notAProcess, UUID.randomUUID(), ActorType.USER, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }
}
