package ru.coordination.approval.api.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.coordination.approval.api.exception.EntityNotFoundException;
import ru.coordination.approval.domain.process.Decision;
import ru.coordination.approval.domain.process.DecisionRepository;
import ru.coordination.approval.service.DecisionService;

/**
 * API-фасад над доменным {@link DecisionService}: отвечает за переход состояния участника
 * и чтение записанных решений.
 *
 * <p>Создан в рамках устранения дефекта сборки: {@code DecisionControllerTest} импортировал
 * {@code ru.coordination.approval.api.service.DecisionService}, которого не существовало —
 * в {@code api.service} был только {@code ApprovalViewService}. Доменная логика при этом не
 * дублируется: принятие решения делегируется {@link DecisionService#decide}.
 */
@Service
@RequiredArgsConstructor
public class DecisionQueryService {

    private final DecisionService decisionService;
    private final DecisionRepository decisionRepository;
    /**
     * Принимает решение участника и возвращает записанную запись {@link Decision}.
     *
     * @throws EntityNotFoundException если решение не было записано (переход не выполнен)
     */
    @Transactional
    public Decision decide(
            UUID processId,
            UUID stageId,
            UUID participantId,
            String decisionType,
            String comment,
            UUID actorId) {

        decisionService.decide(processId, stageId, participantId, decisionType, comment, actorId);

        return decisionRepository.findByParticipantId(participantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Decision was not recorded for participant: " + participantId));
    }

    @Transactional(readOnly = true)
    public Decision getByParticipantId(UUID participantId) {
        return decisionRepository.findByParticipantId(participantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Decision not found for participant: " + participantId));
    }

    @Transactional(readOnly = true)
    public List<Decision> listByProcessId(UUID processId) {
        return decisionRepository.findByProcessId(processId);
    }
}
