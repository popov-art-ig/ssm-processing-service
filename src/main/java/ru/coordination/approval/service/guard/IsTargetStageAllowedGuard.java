package ru.coordination.approval.service.guard;

import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-P-009 «Целевой этап входит в список allowedReturnStages» (PHASE-08).
 * PHASE-08: упрощение — разрешаем возврат на любой этап <= текущего.
 * TODO PHASE-14: проверить allowedReturnStages из шаблона.
 */
@Component("isTargetStageAllowedGuard")
public class IsTargetStageAllowedGuard implements Guard {

    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof ProcessInstance process)) {
            return false;
        }

        Integer targetStageOrderIdx = (Integer) context.parameters().get("targetStageOrderIdx");
        if (targetStageOrderIdx == null) {
            return false;
        }

        StageInstance onReworkStage = process.getStages().stream()
                .filter(s -> "OnRework".equals(s.getStatus()))
                .findFirst()
                .orElse(null);

        if (onReworkStage == null) {
            return false;
        }

        return targetStageOrderIdx >= 1 && targetStageOrderIdx <= onReworkStage.getOrderIdx();
    }
}
