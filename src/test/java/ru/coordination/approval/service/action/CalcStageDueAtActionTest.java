package ru.coordination.approval.service.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.engine.TransitionContext;

/**
 * Единица измерения {@code duration} принята как дни (открытый вопрос №2 тикета PHASE-04,
 * раздел 5) - этот тест сразу показывает, что менять, если предположение окажется неверным.
 */
class CalcStageDueAtActionTest {

    private final CalcStageDueAtAction action = new CalcStageDueAtAction();

    @Test
    void calculatesDueAtAsStartedAtPlusDurationInDays() {
        Instant startedAt = Instant.parse("2026-01-01T00:00:00Z");
        StageInstance stage = StageInstance.builder().startedAt(startedAt).duration(5).build();

        action.execute(new TransitionContext(stage, UUID.randomUUID(), ActorType.SYSTEM, Map.of()));

        assertThat(stage.getDueAt()).isEqualTo(startedAt.plus(5, ChronoUnit.DAYS));
    }
}
