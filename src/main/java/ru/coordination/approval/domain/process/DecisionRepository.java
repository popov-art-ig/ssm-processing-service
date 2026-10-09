package ru.coordination.approval.domain.process;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {
    Optional<Decision> findByParticipantId(UUID participantId);

    List<Decision> findByParticipantIdIn(List<UUID> participantIds);

    @Query("SELECT d FROM Decision d JOIN d.participant p JOIN p.stageIteration i JOIN i.stage s WHERE s.process.id = :processId")
    List<Decision> findByProcessId(@Param("processId") UUID processId);

    /**
     * Число записанных решений по участникам итерации этапа.
     *
     * <p>Используется guard'ом агрегации. Он вызывается как action перехода {@code Decide},
     * то есть ДО того, как {@code TransitionEngine} запишет участнику статус {@code Decided}
     * (запись статуса происходит после выполнения всех actions — см. {@code TransitionEngine}).
     * Поэтому опираться на {@code participant.status} в этот момент нельзя: источник истины —
     * наличие записи {@link Decision}. Коллекция {@code participants} не используется
     * намеренно: в текущей транзакции она может содержать устаревшие экземпляры.
     */
    @Query("select count(d) from Decision d where d.participant.stageIteration.id = :iterationId")
    long countByStageIterationId(@Param("iterationId") UUID iterationId);

    /**
     * Число решений участников итерации, кроме указанного участника.
     *
     * <p>Нужно guard'у агрегации: {@code TransitionEngine} записывает новый статус сущности
     * ДО выполнения actions, поэтому у только что проголосовавшего участника статус уже
     * {@code Decided}, и он обязан исключаться из подсчёта — иначе «все решили» становится
     * истинным после первого же решения.
     */
    @Query("select count(d) from Decision d where d.participant.stageIteration.id = :iterationId "
            + "and d.participant.id <> :excludedParticipantId")
    long countByStageIterationIdExcluding(
            @Param("iterationId") UUID iterationId,
            @Param("excludedParticipantId") UUID excludedParticipantId);
}
