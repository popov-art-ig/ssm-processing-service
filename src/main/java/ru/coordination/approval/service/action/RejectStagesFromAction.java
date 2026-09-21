package ru.coordination.approval.service.action;

import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

/**
 * A-RT-002 «Присваивает статус Rejected этапам между целевым и текущим» (PHASE-08).
 * Используется в переходе ResumeProcess для отклонения этапов, которые нужно пропустить.
 */
@Component("rejectStagesFromAction")
public class RejectStagesFromAction implements Action {

    @Override
    public void execute(TransitionContext context) {
        ProcessInstance process = (ProcessInstance) context.entity();
        Integer targetStageOrderIdx = (Integer) context.parameters().get("targetStageOrderIdx");

        StageInstance onReworkStage = process.getStages().stream()
                .filter(s -> "OnRework".equals(s.getStatus()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No OnRework stage found"));

        process.getStages().stream()
                .filter(s -> s.getOrderIdx() > targetStageOrderIdx
                        && s.getOrderIdx() <= onReworkStage.getOrderIdx())
                .forEach(stage -> stage.setStatus("Rejected"));
    }
}
