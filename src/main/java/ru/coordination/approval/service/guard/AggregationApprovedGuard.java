package ru.coordination.approval.service.guard;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.common.DecisionMode;
import ru.coordination.approval.domain.process.Decision;
import ru.coordination.approval.domain.process.DecisionRepository;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

@Component("aggregationApprovedGuard")
@RequiredArgsConstructor
public class AggregationApprovedGuard implements Guard {

    private final DecisionRepository decisionRepository;

    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof StageInstance stage)) {
            return false;
        }

        StageIteration currentIteration = stage.getIterations().stream()
                .max((a, b) -> Integer.compare(a.getIterationIdx(), b.getIterationIdx()))
                .orElse(null);

        if (currentIteration == null) {
            return false;
        }

        List<Participant> participants = currentIteration.getParticipants();
        List<Decision> decisions = participants.stream()
                .map(p -> decisionRepository.findByParticipantId(p.getId()).orElse(null))
                .filter(d -> d != null)
                .collect(java.util.stream.Collectors.toList());

        if (decisions.isEmpty()) {
            return false;
        }

        DecisionMode mode = stage.getDecisionMode() != null ? stage.getDecisionMode() : DecisionMode.AND;

        return switch (mode) {
            case AND -> decisions.stream().allMatch(d -> "APPROVE".equals(d.getResult()));
            case ANY_APPROVE -> decisions.stream().anyMatch(d -> "APPROVE".equals(d.getResult()));
            case ANY_REJECT -> decisions.stream().noneMatch(d -> "REJECT".equals(d.getResult()));
            case ANY_DECISION -> true;
            case FIRST_REJECT_FAIL_FAST -> decisions.stream().allMatch(d -> "APPROVE".equals(d.getResult()));
        };
    }
}
