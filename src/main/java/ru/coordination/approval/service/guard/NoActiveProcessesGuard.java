package ru.coordination.approval.service.guard;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.ProcessRepository;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Guard;

@Component("noActiveProcesses")
@RequiredArgsConstructor
public class NoActiveProcessesGuard implements Guard {

    private final ProcessRepository processRepository;

    private static final List<String> TERMINAL_STATUSES = List.of(
            "Approved", "Rejected", "Canceled", "ApprovedWithComments"
    );

    @Override
    public boolean evaluate(TransitionContext context) {
        if (!(context.entity() instanceof Template template)) {
            return false;
        }

        UUID templateId = template.getId();
        List<ProcessInstance> processes = processRepository.findByTemplateRef(templateId);

        for (ProcessInstance process : processes) {
            if (!TERMINAL_STATUSES.contains(process.getStatus())) {
                return false;
            }
        }

        return true;
    }
}
