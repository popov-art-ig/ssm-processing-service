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
 * Guard сравнивает статусы <b>остальных</b> этапов: закрываемый этап приходит в параметре
 * {@code excludedStageId} и исключается, потому что TransitionEngine записывает его новый статус
 * до выполнения actions (см. javadoc {@link AllStagesCompletedGuard}).
 */
@ExtendWith(MockitoExtension.class)
class AllStagesCompletedGuardTest {

    @Mock
    private StageRepository stageRepository;

    private AllStagesCompletedGuard guard;

    @BeforeEach
    void setUp() {
        guard = new AllStagesCompletedGuard(stageRepository);
    }

    @Test
    void returnsTrueWhenAllStagesApproved() {
        assertThat(evaluate("Approved", "Approved")).isTrue();
    }

    @Test
    void returnsTrueWhenAllStagesApprovedWithComments() {
        assertThat(evaluate("ApprovedWithComments", "ApprovedWithComments")).isTrue();
    }

    @Test
    void returnsTrueWhenMixOfApprovedAndApprovedWithComments() {
        assertThat(evaluate("Approved", "ApprovedWithComments")).isTrue();
    }

    @Test
    void returnsTrueWhenAllStagesCompleted() {
        assertThat(evaluate("Completed", "Completed")).isTrue();
    }

    @Test
    void returnsTrueWhenMixOfApprovedAndCompleted() {
        assertThat(evaluate("Approved", "Completed", "ApprovedWithComments")).isTrue();
    }

    @Test
    void returnsTrueWhenTheOnlyStageIsApproved() {
        // Процесс из одного этапа: после его закрытия процесс завершён.
        assertThat(evaluate("Approved")).isTrue();
    }

    @Test
    void returnsFalseWhenOneStageIsPending() {
        assertThat(evaluate("Approved", "Pending")).isFalse();
    }

    @Test
    void returnsFalseWhenOneStageIsActive() {
        assertThat(evaluate("Approved", "Active")).isFalse();
    }

    @Test
    void returnsFalseWhenOneStageIsOnRework() {
        assertThat(evaluate("Approved", "OnRework")).isFalse();
    }

    @Test
    void returnsFalseWhenProcessHasNoStagesAtAll() {
        assertThat(evaluate()).isFalse();
    }

    @Test
    void returnsFalseWhenEntityIsNotProcessInstance() {
        TransitionContext context =
                new TransitionContext("some string", UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    /** @param statuses статусы всех этапов процесса */
    private boolean evaluate(String... statuses) {
        ProcessInstance process = process();

        when(stageRepository.findStatusesByProcessId(eq(process.getId())))
                .thenReturn(Arrays.asList(statuses));

        TransitionContext context =
                new TransitionContext(process, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        return guard.evaluate(context);
    }

    private ProcessInstance process() {
        return ProcessInstance.builder()
                .id(UUID.randomUUID())
                .processType(ProcessType.STANDARD)
                .configVersion(1)
                .status("InProgress")
                .initiatorId(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();
    }
}
