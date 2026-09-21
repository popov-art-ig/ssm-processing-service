package ru.coordination.approval.service.action;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.template.Template;
import ru.coordination.approval.domain.template.TemplateRepository;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

@Component("setPublishedTimestamp")
@RequiredArgsConstructor
public class SetPublishedTimestampAction implements Action {

    private final TemplateRepository templateRepository;

    @Override
    public void execute(TransitionContext context) {
        if (!(context.entity() instanceof Template template)) {
            return;
        }

        template.setPublishedAt(Instant.now());
        template.setPublishedBy(context.actorId());
        templateRepository.save(template);
    }
}
