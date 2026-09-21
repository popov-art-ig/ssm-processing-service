package ru.coordination.approval.service.guard;

import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-P-007 «Хотя бы один этап процесса в статусе OnRework» (PHASE-08).
 * Используется в переходе ProcessRework для автоматического перевода процесса в OnRework.
 */
@Component("hasStageOnReworkGuard")
public class HasStageOnReworkGuard implements Guard {

    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof ProcessInstance process)) {
            return false;
        }

        return process.getStages().stream()
                .anyMatch(stage -> "OnRework".equals(stage.getStatus()));
    }
}
