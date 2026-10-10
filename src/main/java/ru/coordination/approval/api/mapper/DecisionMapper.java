package ru.coordination.approval.api.mapper;

import org.springframework.stereotype.Component;
import ru.coordination.approval.api.dto.decision.DecisionDto;
import ru.coordination.approval.domain.process.Decision;
import ru.coordination.approval.domain.process.DecisionRepository;
import ru.coordination.approval.domain.process.Participant;

/**
 * Преобразование {@code Decision} в {@link DecisionDto} и проверка наличия решения у участника.
 *
 * <p>Данные берутся из самой записи решения; единственное поле, которое заполнить нечем, —
 * {@code decidedBy}: автора в {@code Decision} нет, он выводится из участника, и DTO этого не
 * делает.
 */
@Component
public class DecisionMapper {

    private final DecisionRepository decisionRepository;

    public DecisionMapper(DecisionRepository decisionRepository) {
        this.decisionRepository = decisionRepository;
    }

    /** Решение участника. {@code decidedBy} всегда {@code null} — автора в {@code Decision} нет. */
    public DecisionDto toDto(Decision decision) {
        return new DecisionDto(
                decision.getId(),
                decision.getParticipant().getId(),
                decision.getResult(),
                decision.getComment(),
                decision.getRecordedAt(),
                null // decidedBy field doesn't exist in Decision
        );
    }

    /**
     * Есть ли у участника решение.
     *
     * <p>Ищет {@code Decision} по идентификатору <b>участника</b> — это работает, пока PK решения
     * совпадает с PK участника (так их создаёт {@code RecordDecisionAction}). Отдельного
     * {@code exists}-запроса нет: вызывающий получает {@code Boolean}, а не {@code boolean}, но
     * {@code null} здесь никогда не возвращается.
     */
    public Boolean hasDecision(Participant participant) {
        return decisionRepository.findById(participant.getId()).isPresent();
    }
}
