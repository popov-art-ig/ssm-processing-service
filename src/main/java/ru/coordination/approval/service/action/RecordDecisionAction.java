package ru.coordination.approval.service.action;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.domain.process.Decision;
import ru.coordination.approval.domain.process.DecisionRepository;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.engine.TransitionContext;
import ru.coordination.approval.engine.registry.Action;

@Component("recordDecisionAction")
@RequiredArgsConstructor
public class RecordDecisionAction implements Action {

    private final DecisionRepository decisionRepository;

    @Override
    public void execute(TransitionContext context) {
        Participant participant = (Participant) context.entity();

        String decisionResult = (String) context.parameters().get("decisionType");
        String comment = (String) context.parameters().get("comment");

        Decision decision = Decision.builder()
                // BaseEntity намеренно объявляет @Id без @GeneratedValue (генерирует приложение),
                // поэтому id обязан быть проставлен до save(): иначе Hibernate падает с
                // "Identifier of entity ... must be manually assigned before calling 'persist()'".
                // Это ломало все сценарии принятия решения (находка сборки).
                .id(UUID.randomUUID())
                .participant(participant)
                .result(decisionResult)
                .comment(comment)
                .auto(false)
                .recordedAt(Instant.now())
                .createdAt(Instant.now())
                .build();

        decisionRepository.save(decision);
    }
}
