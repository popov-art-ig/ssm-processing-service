package ru.coordination.approval.api.mapper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.coordination.approval.api.dto.decision.DecisionDto;
import ru.coordination.approval.domain.process.Decision;
import ru.coordination.approval.domain.process.DecisionRepository;

/**
 * Преобразование {@code Decision} в {@link DecisionDto} и пакетная проверка наличия решения.
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
     * Идентификаторы участников, у которых есть решение, — <b>одним</b> запросом на весь набор.
     *
     * <p>Раньше здесь был {@code hasDecision(Participant)} с {@code findById} на каждого участника:
     * для агрегата или страницы это давало запрос на строку, причём для участника без решения
     * каждый повторный вызов снова шёл в БД (отрицательного кеша у Hibernate нет). Метод удалён, а
     * не оставлен «для одиночных случаев»: иначе N+1 вернулся бы при первом же новом вызове
     * (замеры — {@code doc/LOAD_TESTING.md} §5).
     *
     * @param participantIds участники, про которых нужно узнать наличие решения; пустой набор
     *                       возвращает пустой результат без обращения к БД
     */
    public Set<UUID> participantIdsWithDecision(Collection<UUID> participantIds) {
        if (participantIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(decisionRepository.findParticipantIdsWithDecision(participantIds));
    }
}
