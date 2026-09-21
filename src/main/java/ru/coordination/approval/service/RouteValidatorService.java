package ru.coordination.approval.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;
import ru.coordination.approval.exception.RequiredSlotNotResolvedException;

@Service
@RequiredArgsConstructor
public class RouteValidatorService {

    public void validate(ProcessInstance process) {
        if (process.getStages().isEmpty()) {
            throw new IllegalArgumentException("Process must have at least one stage");
        }

        for (StageInstance stage : process.getStages()) {
            validateStage(stage);
        }
    }

    private void validateStage(StageInstance stage) {
        List<StageIteration> iterations = stage.getIterations();
        if (iterations.isEmpty()) {
            throw new IllegalArgumentException(
                    "Stage " + stage.getOrderIdx() + " must have at least one iteration"
            );
        }

        StageIteration iteration = iterations.get(0);

        // If stage has participants with required roles, at least one must be resolved
        // For now, we check if there are any participants at all
        // In a real system, we'd check template's required slots
        if (iteration.getParticipants().isEmpty()) {
            throw new RequiredSlotNotResolvedException(
                    "Stage " + stage.getOrderIdx() + " has required slots that were not resolved"
            );
        }
    }
}
