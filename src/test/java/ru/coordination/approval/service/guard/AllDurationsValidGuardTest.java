package ru.coordination.approval.service.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.coordination.approval.domain.audit.ActorType;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.engine.TransitionContext;

class AllDurationsValidGuardTest {

    private final AllDurationsValidGuard guard = new AllDurationsValidGuard();

    @Test
    void passesWhenAllStageDurationsArePositive() {
        boolean result = evaluate(stage(1), stage(5));

        assertThat(result).isTrue();
    }

    @Test
    void failsWhenAnyStageDurationIsZeroOrNegative() {
        boolean result = evaluate(stage(1), stage(0));

        assertThat(result).isFalse();
    }

    @Test
    void failsWhenAnyStageDurationIsNull() {
        boolean result = evaluate(stage(1), StageInstance.builder().duration(null).build());

        assertThat(result).isFalse();
    }

    private boolean evaluate(StageInstance... stages) {
        ProcessInstance process = ProcessInstance.builder().stages(List.of(stages)).build();
        return guard.evaluate(new TransitionContext(process, UUID.randomUUID(), ActorType.USER, Map.of()));
    }

    private static StageInstance stage(int duration) {
        return StageInstance.builder().duration(duration).build();
    }
}
