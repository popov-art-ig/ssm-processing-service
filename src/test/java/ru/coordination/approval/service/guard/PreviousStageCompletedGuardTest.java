package ru.coordination.approval.service.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageRepository;
import ru.coordination.approval.engine.TransitionContext;

/**
 * Guard читает статус предыдущего этапа из {@link StageRepository}, а не из
 * {@code process.getStages()}: он вызывается как action перехода закрытия этапа, когда новый
 * статус ещё не записан в сущность (см. javadoc {@link PreviousStageCompletedGuard}).
 */
@ExtendWith(MockitoExtension.class)
class PreviousStageCompletedGuardTest {

    @Mock
    private StageRepository stageRepository;

    private PreviousStageCompletedGuard guard;

    @BeforeEach
    void setUp() {
        guard = new PreviousStageCompletedGuard(stageRepository);
    }

    @Test
    void passesWhenNoPreviousStage() {
        UUID processId = UUID.randomUUID();
        StageInstance first = stage(processId, 0, "Active");

        when(stageRepository.findPreviousStageIds(any(), anyInt())).thenReturn(List.of());

        assertThat(evaluate(first)).isTrue();
    }

    @Test
    void passesWhenPreviousStageIsApproved() {
        assertThat(evaluateWithPreviousStatus("Approved")).isTrue();
    }

    @Test
    void passesWhenPreviousStageIsApprovedWithComments() {
        assertThat(evaluateWithPreviousStatus("ApprovedWithComments")).isTrue();
    }

    @Test
    void passesWhenPreviousStageIsCompleted() {
        assertThat(evaluateWithPreviousStatus("Completed")).isTrue();
    }

    @Test
    void failsWhenPreviousStageIsNotInAFinalStatus() {
        assertThat(evaluateWithPreviousStatus("Active")).isFalse();
    }

    private boolean evaluateWithPreviousStatus(String previousStatus) {
        UUID processId = UUID.randomUUID();
        UUID previousId = UUID.randomUUID();
        StageInstance second = stage(processId, 1, "Pending");

        when(stageRepository.findPreviousStageIds(any(), anyInt())).thenReturn(List.of(previousId));
        when(stageRepository.findById(previousId))
                .thenReturn(Optional.of(stage(processId, 0, previousStatus)));

        return evaluate(second);
    }

    private boolean evaluate(StageInstance stage) {
        return guard.evaluate(new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of()));
    }

    private static StageInstance stage(UUID processId, int orderIdx, String status) {
        ProcessInstance process = ProcessInstance.builder().id(processId).build();
        return StageInstance.builder()
                .id(UUID.randomUUID())
                .process(process)
                .orderIdx(orderIdx)
                .status(status)
                .build();
    }
}
