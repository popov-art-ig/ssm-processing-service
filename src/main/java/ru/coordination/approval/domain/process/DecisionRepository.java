package ru.coordination.approval.domain.process;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Доступ к {@link Decision} — решениям участников. Два счётных запроса здесь появились из-за
 * порядка операций в {@code TransitionEngine}: решение проверяется в момент, когда статус
 * участника ещё не записан, поэтому источником истины служат строки {@code decision}, а не
 * {@code participant.status} (см. {@code AllParticipantsDecidedGuard}).
 */
public interface DecisionRepository extends JpaRepository<Decision, UUID> {

    /** Решение участника; пусто, если участник ещё не голосовал. */
    Optional<Decision> findByParticipantId(UUID participantId);

    /** Решения по списку участников — пакетная загрузка для агрегированных представлений. */
    List<Decision> findByParticipantIdIn(List<UUID> participantIds);

    /**
     * Идентификаторы участников, у которых уже есть решение, — одним запросом на весь набор.
     *
     * <p>Проекция, а не загрузка сущностей: для флага {@code ParticipantDto.hasDecision} нужны
     * только идентификаторы, и обращение {@code d.participant.id} не поднимает ни решение, ни
     * участника. Существует потому, что прежний способ — спрашивать по одному участнику за раз —
     * давал N+1: агрегат на процессе с 10 этапами делал 30 лишних запросов, а страница списка
     * множила их на число строк (doc/LOAD_TESTING.md §5).
     */
    @Query("select d.participant.id from Decision d where d.participant.id in :participantIds")
    List<UUID> findParticipantIdsWithDecision(@Param("participantIds") Collection<UUID> participantIds);

    /** Все решения процесса: подъём по цепочке участник → итерация → этап; для представлений процесса. */
    @Query("SELECT d FROM Decision d JOIN d.participant p JOIN p.stageIteration i JOIN i.stage s WHERE s.process.id = :processId")
    List<Decision> findByProcessId(@Param("processId") UUID processId);

    /**
     * Число записанных решений по участникам итерации этапа.
     *
     * <p>Используется guard'ом агрегации. Он вызывается как action перехода {@code Decide},
     * то есть ДО того, как {@code TransitionEngine} запишет участнику статус {@code Decided}.
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
