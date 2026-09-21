package ru.coordination.approval.service.guard;

import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

@Component("isAssignedParticipantGuard")
public class IsAssignedParticipantGuard implements Guard {

    @Override
    public boolean execute(TransitionContext context) {
        if (!(context.entity() instanceof Participant participant)) {
            return false;
        }

        return context.actorId() != null
                && context.actorId().equals(participant.getUserId())
                && "Assigned".equals(participant.getStatus());
    }
}
