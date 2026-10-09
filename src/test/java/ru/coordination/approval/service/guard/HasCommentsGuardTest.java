package ru.coordination.approval.service.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageRepository;
import ru.coordination.approval.engine.TransitionContext;

/**
 * Guard читает статусы этапов из {@link StageRepository}: вызывается внутри перехода закрытия
 * этапа, когда в памяти закрываемый этап ещё может быть {@code Active} — проверка по
 * {@code process.getStages()} теряла признак «с замечаниями».
 */
@ExtendWith(MockitoExtension.class)
class HasCommentsGuardTest {

    @Mock
    private StageRepository stageRepository;

    private HasCommentsGuard guard;

    @BeforeEach
    void setUp() {
        guard = new HasCommentsGuard(stageRepository);
    }

    @Test
    void returnsTrueWhenAtLeastOneStageApprovedWithComments() {
        assertThat(evaluate("Approved", "ApprovedWithComments", "Approved")).isTrue();
    }

    @Test
    void returnsTrueWhenAllStagesApprovedWithComments() {
        assertThat(evaluate("ApprovedWithComments", "ApprovedWithComments")).isTrue();
    }

    @Test
    void returnsTrueWhenFirstStageHasComments() {
        assertThat(evaluate("ApprovedWithComments", "Approved", "Approved")).isTrue();
    }

    @Test
    void returnsTrueWhenLastStageHasComments() {
        assertThat(evaluate("Approved", "Approved", "ApprovedWithComments")).isTrue();
    }

    @Test
    void returnsFalseWhenAllStagesApproved() {
        assertThat(evaluate("Approved", "Approved", "Approved")).isFalse();
    }

    @Test
    void returnsFalseWhenStagesArePending() {
        assertThat(evaluate("Pending", "Pending", "Pending")).isFalse();
    }

    @Test
    void returnsFalseWhenStagesAreOnRework() {
        assertThat(evaluate("OnRework", "Approved", "Approved")).isFalse();
    }

    @Test
    void returnsFalseWhenMixOfApprovedAndCompleted() {
        assertThat(evaluate("Approved", "Completed", "Approved")).isFalse();
    }

    @Test
    void returnsFalseWhenProcessHasNoStages() {
        assertThat(evaluate()).isFalse();
    }

    @Test
    void returnsFalseWhenEntityIsNotProcessInstance() {
        TransitionContext context =
                new TransitionContext("some string", UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    private boolean evaluate(String... statuses) {
        ProcessInstance process = ProcessInstance.builder()
                .id(UUID.randomUUID())
                .processType(ProcessType.STANDARD)
                .configVersion(1)
                .status("InProgress")
                .initiatorId(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();

        when(stageRepository.findStatusesByProcessId(eq(process.getId())))
                .thenReturn(Arrays.asList(statuses));

        TransitionContext context =
                new TransitionContext(process, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        return guard.evaluate(context);
    }
}
