package ru.coordination.approval.service.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.StageType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageRepository;
import ru.coordination.approval.engine.TransitionContext;

/**
 * Guard читает статусы этапов из {@link StageRepository} и допускает возврат на любой этап
 * не позже ушедшего на доработку, включая самый первый ({@code orderIdx = 0}).
 */
@ExtendWith(MockitoExtension.class)
class IsTargetStageAllowedGuardTest {

    @Mock
    private StageRepository stageRepository;

    private IsTargetStageAllowedGuard guard;

    @BeforeEach
    void setUp() {
        guard = new IsTargetStageAllowedGuard(stageRepository);
    }

    @Test
    void returnsTrueWhenTargetStageIsBeforeOnReworkStage() {
        assertThat(evaluate(2, 0)).isTrue();
    }

    @Test
    void returnsTrueWhenTargetStageIsSameAsOnReworkStage() {
        assertThat(evaluate(2, 2)).isTrue();
    }

    @Test
    void returnsTrueWhenTargetStageIsZero() {
        // Этапы нумеруются с 0 — возврат на первый этап должен быть разрешён.
        assertThat(evaluate(1, 0)).isTrue();
    }

    @Test
    void returnsFalseWhenTargetStageIsAfterOnReworkStage() {
        assertThat(evaluate(1, 2)).isFalse();
    }

    @Test
    void returnsFalseWhenTargetStageOrderIdxIsAbsent() {
        ProcessInstance process = process();

        // TransitionContext копирует параметры через Map.copyOf, который не допускает null,
        // поэтому «параметр не задан» проверяется отсутствием ключа.
        TransitionContext context =
                new TransitionContext(process, UUID.randomUUID(), ActorType.USER, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseWhenNoOnReworkStage() {
        ProcessInstance process = process();

        List<StageInstance> onlyApproved = new ArrayList<>();
        StageInstance stage = StageInstance.builder()
                .process(process)
                .orderIdx(1)
                .stageType(StageType.APPROVAL)
                .status("Active")
                .createdAt(Instant.now())
                .build();
        onlyApproved.add(stage);

        when(stageRepository.findByProcessIdOrderByOrderIdx(eq(process.getId())))
                .thenReturn(onlyApproved);

        TransitionContext context = new TransitionContext(
                process, UUID.randomUUID(), ActorType.USER, Map.of("targetStageOrderIdx", 1));

        assertThat(guard.evaluate(context)).isFalse();
    }

    @Test
    void returnsFalseWhenEntityIsNotProcessInstance() {
        TransitionContext context = new TransitionContext(
                "some string", UUID.randomUUID(), ActorType.USER, Map.of("targetStageOrderIdx", 1));

        assertThat(guard.evaluate(context)).isFalse();
    }

    private boolean evaluate(int onReworkOrderIdx, Integer targetOrderIdx) {
        ProcessInstance process = process();

        when(stageRepository.findByProcessIdOrderByOrderIdx(eq(process.getId())))
                .thenReturn(stages(onReworkOrderIdx));

        TransitionContext context = new TransitionContext(
                process, UUID.randomUUID(), ActorType.USER, Map.of("targetStageOrderIdx", targetOrderIdx));

        return guard.evaluate(context);
    }


    /** @return этапы 0..onReworkOrderIdx, последний в статусе OnRework */
    private List<StageInstance> stages(int onReworkOrderIdx) {
        ProcessInstance process = process();
        List<StageInstance> stages = new ArrayList<>();
        for (int i = 0; i <= onReworkOrderIdx; i++) {
            stages.add(StageInstance.builder()
                    .process(process)
                    .orderIdx(i)
                    .stageType(StageType.APPROVAL)
                    .status(i == onReworkOrderIdx ? "OnRework" : "Approved")
                    .createdAt(Instant.now())
                    .build());
        }
        return stages;
    }

    private ProcessInstance process() {
        return ProcessInstance.builder()
                .id(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();
    }
}
