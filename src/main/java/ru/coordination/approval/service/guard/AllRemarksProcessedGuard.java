package ru.coordination.approval.service.guard;

import org.springframework.stereotype.Component;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-P-008 «Все замечания процесса обработаны» (PHASE-08).
 * PHASE-08: заглушка — всегда разрешаем возобновление.
 * TODO PHASE-11: реализовать проверку таблицы remark.
 */
@Component("allRemarksProcessedGuard")
public class AllRemarksProcessedGuard implements Guard {

    @Override
    public boolean evaluate(TransitionContext context) {
        return true;
    }
}
