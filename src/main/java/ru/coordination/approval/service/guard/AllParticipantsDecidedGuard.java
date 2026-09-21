package ru.coordination.approval.service.guard;

import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

@Component("allParticipantsDecidedGuard")
public class AllParticipantsDecidedGuard implements Guard {

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

        var participants = currentIteration.getParticipants();
        if (participants.isEmpty()) {
            return false;
        }

        return participants.stream().allMatch(p ->
                "Decided".equals(p.getStatus()) || "AutoApproved".equals(p.getStatus()));
    }
}
