package ru.coordination.approval.service.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.common.DecisionMode;
import ru.coordination.approval.domain.common.StageType;
import ru.coordination.approval.domain.process.DecisionRepository;
import ru.coordination.approval.domain.process.ParticipantRepository;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.engine.TransitionContext;

/**
 * Guard исключает сущность текущего перехода из подсчёта: TransitionEngine записывает её новый
 * статус до выполнения actions, поэтому «все решили» иначе становится истинным после первого же
 * решения (см. javadoc {@link AllParticipantsDecidedGuard}).
 */
@ExtendWith(MockitoExtension.class)
class AllParticipantsDecidedGuardTest {

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private DecisionRepository decisionRepository;

    private AllParticipantsDecidedGuard guard;

    @BeforeEach
    void setUp() {
        guard = new AllParticipantsDecidedGuard(participantRepository, decisionRepository);
    }

    @Test
    void returnsTrueWhenAllOtherParticipantsHaveDecided() {
        assertThat(evaluate(3, 2, 2)).isTrue();
    }

    @Test
    void returnsTrueWhenParticipantIsTheOnlyOne() {
        // Остальных нет => агрегировать больше нечего.
        assertThat(evaluate(1, 0, 0)).isTrue();
    }

    @Test
    void returnsFalseWhenOneOtherParticipantHasNotDecidedYet() {
        assertThat(evaluate(3, 2, 1)).isFalse();
    }

    @Test
    void returnsFalseWhenIterationHasNoParticipants() {
        StageInstance stage = stage();
        UUID iterationId = stage.getIterations().get(0).getId();

        // Ранний выход до обращения к счётчикам решений — стабим только нужный вызов.
        when(participantRepository.countByStageIterationId(eq(iterationId))).thenReturn(0L);

        assertThat(guard.evaluate(context(stage, UUID.randomUUID()))).isFalse();
    }

    @Test
    void returnsFalseWhenNoIterations() {
        StageInstance stage = StageInstance.builder()
                .stageType(StageType.APPROVAL)
                .duration(3)
                .status("Active")
                .createdAt(Instant.now())
                .build();

        assertThat(guard.evaluate(context(stage, UUID.randomUUID()))).isFalse();
    }

    @Test
    void returnsFalseWhenEntityIsNotStageInstance() {
        TransitionContext context =
                new TransitionContext("some string", UUID.randomUUID(), ActorType.SYSTEM, Map.of());

        assertThat(guard.evaluate(context)).isFalse();
    }

    private boolean evaluate(long totalParticipants, long otherParticipants, long otherDecisions) {
        StageInstance stage = stage();
        UUID iterationId = stage.getIterations().get(0).getId();
        // Голосующий участник приходит как actorId контекста перехода.
        UUID currentParticipantId = UUID.randomUUID();

        when(participantRepository.countByStageIterationId(eq(iterationId)))
                .thenReturn(totalParticipants);
        when(participantRepository.countByStageIterationIdExcluding(eq(iterationId), eq(currentParticipantId)))
                .thenReturn(otherParticipants);
        when(decisionRepository.countByStageIterationIdExcluding(eq(iterationId), eq(currentParticipantId)))
                .thenReturn(otherDecisions);

        return guard.evaluate(context(stage, currentParticipantId));
    }

    private TransitionContext context(StageInstance stage, UUID participantId) {
        return new TransitionContext(stage, participantId, ActorType.SYSTEM, Map.of());
    }

    private StageInstance stage() {
        ProcessInstance process = ProcessInstance.builder()
                .createdAt(Instant.now())
                .build();

        StageInstance stage = StageInstance.builder()
                .process(process)
                .stageType(StageType.APPROVAL)
                .duration(3)
                .decisionMode(DecisionMode.AND)
                .status("Active")
                .createdAt(Instant.now())
                .build();

        StageIteration iteration = StageIteration.builder()
                .id(UUID.randomUUID())
                .stage(stage)
                .iterationIdx(1)
                .status("Active")
                .startedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        stage.getIterations().add(iteration);
        return stage;
    }
}
