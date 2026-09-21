package ru.coordination.approval.service.guard;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.template.SlotTemplate;
import ru.coordination.approval.domain.template.StageTemplate;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

@Component("allMandatorySlotsValid")
@RequiredArgsConstructor
public class AllMandatorySlotsValidGuard implements Guard {

    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof Template template)) {
            return false;
        }

        List<StageTemplate> stages = template.getStages();
        if (stages == null || stages.isEmpty()) {
            return false;
        }

        for (StageTemplate stage : stages) {
            List<SlotTemplate> slots = stage.getActorSlots();
            if (slots == null) {
                continue;
            }

            for (SlotTemplate slot : slots) {
                if (slot.isRequired()) {
                    boolean hasUserId = slot.getUserId() != null;
                    boolean hasRoles = slot.getAcceptableRoles() != null && !slot.getAcceptableRoles().isEmpty();

                    if (!hasUserId && !hasRoles) {
                        return false;
                    }
                }
            }
        }

        return true;
    }
}
