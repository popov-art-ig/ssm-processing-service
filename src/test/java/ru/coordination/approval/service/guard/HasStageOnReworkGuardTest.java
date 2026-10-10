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
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageRepository;
import ru.coordination.approval.engine.TransitionContext;

/**
 * Guard читает статусы этапов из {@link StageRepository}: он вызывается как action перехода
 * закрытия этапа, то есть до записи нового статуса в сущность (см. javadoc
 * {@link HasStageOnReworkGuard}).
 */
@ExtendWith(MockitoExtension.class)
class HasStageOnReworkGuardTest {

    @Mock
    private StageRepository stageRepository;

    private HasStageOnReworkGuard guard;

    @BeforeEach
    void setUp() {
        guard = new HasStageOnReworkGuard(stageRepository);
    }

    @Test
    void returnsTrueWhenAtLeastOneStageIsOnRework() {
        assertThat(evaluateWithStatuses("Approved", "OnRework")).isTrue();
    }

    @Test
    void returnsFalseWhenNoStageIsOnRework() {
        assertThat(evaluateWithStatuses("Approved", "Active")).isFalse();
    }

    @Test
    void returnsFalseWhenAllStagesAreApproved() {
        assertThat(evaluateWithStatuses("Approved")).isFalse();
    }

    @Test
    void returnsFalseWhenEntityIsNotProcessInstance() {
        TransitionContext context =
                new TransitionContext("some string", UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    private boolean evaluateWithStatuses(String... statuses) {
        ProcessInstance process = ProcessInstance.builder()
                .id(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();

        when(stageRepository.findStatusesByProcessId(eq(process.getId())))
                .thenReturn(Arrays.asList(statuses));

        TransitionContext context =
                new TransitionContext(process, UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        return guard.evaluate(context);
    }
}
