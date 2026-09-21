package ru.coordination.approval.service.action;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

@Component("setArchivedTimestamp")
@RequiredArgsConstructor
public class SetArchivedTimestampAction implements Action {

    private final TemplateRepository templateRepository;

    @Override
    public void execute(TransitionContext context) {
        if (!(context.entity() instanceof Template template)) {
            return;
        }

        template.setUpdatedAt(Instant.now());
        templateRepository.save(template);
    }
}
