package ru.coordination.approval.service.guard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.RemarkRepository;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

/**
 * G-P-010 «У процесса есть необработанные замечания» (PHASE-11).
 * Используется для блокировки возобновления процесса при наличии необработанных замечаний.
 */
@Component("hasUnprocessedRemarksGuard")
@RequiredArgsConstructor
public class HasUnprocessedRemarksGuard implements Guard {

    private final RemarkRepository remarkRepository;

    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof ProcessInstance process)) {
            return false;
        }

        return remarkRepository.existsByProcessIdAndStatusIn(
                process.getId(),
                java.util.List.of("Open", "InProgress"));
    }
}
