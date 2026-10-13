package ru.coordination.approval.api.mapper;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.api.dto.process.IterationDto;
import ru.coordination.approval.api.dto.process.ParticipantDto;
import ru.coordination.approval.api.dto.process.ProcessDto;
import ru.coordination.approval.api.dto.process.StageDto;
import ru.coordination.approval.api.dto.view.ProcessSummaryDto;
import ru.coordination.approval.domain.process.Participant;
import ru.coordination.approval.domain.process.ProcessInstance;
import ru.coordination.approval.domain.process.StageInstance;
import ru.coordination.approval.domain.process.StageIteration;

/**
 * Преобразование агрегата процесса в DTO API: {@code ProcessInstance} → {@link ProcessDto},
 * {@code StageInstance} → {@link StageDto}, {@code StageIteration} → {@link IterationDto},
 * {@code Participant} → {@link ParticipantDto}.
 *
 * <p>Маппер только читает уже загруженный агрегат, но обходит ленивые коллекции
 * ({@code stages → iterations → participants}), поэтому вызывать его можно лишь внутри открытой
 * сессии — этим занимается {@code ProcessViewService}.
 *
 * <p>Что теряется и вычисляется:
 * <ul>
 *   <li>{@code IterationDto.dueDate} всегда {@code null} — у {@code StageIteration} нет поля срока,
 *       сроки хранятся на этапе и участнике;</li>
 *   <li>{@code ParticipantDto.hasDecision} берётся из набора, посчитанного <b>одним</b> запросом на
 *       процесс: раньше флаг выяснялся отдельным запросом на каждого участника, из-за чего агрегат
 *       на 10 этапах делал 30 лишних запросов, а список процессов — сотни (N+1, замеры в
 *       {@code doc/LOAD_TESTING.md} §5);</li>
 *   <li>{@code enum}-поля ({@code processType}, {@code stageType}, {@code decisionMode})
 *       передаются строками и защищены от {@code null}: {@code process_type} может быть пустым у
 *       карты шаблона.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ProcessMapper {

    private final DecisionMapper decisionMapper;

    /**
     * Собирает детальное представление процесса вместе с этапами, итерациями и участниками.
     * Коллекции обходятся лениво вглубь, поэтому метод обязан вызываться внутри открытой сессии
     * (в коде это {@code ProcessViewService} под {@code readOnly}-транзакцией).
     *
     * <p>Наличие решений выясняется одним запросом на весь процесс ({@link #toDto(ProcessInstance,
     * Set)}); вызывающий, у которого набор уже есть, должен пользоваться перегрузкой с набором,
     * чтобы не делать запрос второй раз.
     */
    public ProcessDto toDto(ProcessInstance process) {
        return toDto(process, decisionMapper.participantIdsWithDecision(participantIds(process)));
    }

    /**
     * То же, но с уже известным набором участников, у которых есть решение: агрегированное
     * представление загружает решения пакетно и может переиспользовать свой набор.
     */
    public ProcessDto toDto(ProcessInstance process, Set<UUID> decisionParticipantIds) {
        return new ProcessDto(
                process.getId(),
                process.getEntityType(),
                process.getEntitySubtype(),
                process.getEntityId(),
                // process_type у ProcessInstance обязателен, но guard оставлен осознанно: раньше
                // незащищённый .name() на nullable-поле уже давал 500 (карты шаблонов), а цена
                // проверки нулевая.
                process.getProcessType() == null ? null : process.getProcessType().name(),
                process.getStatus(),
                process.getInitiatorId(),
                process.getResponsibleUserId(),
                process.getCreatedAt(),
                process.getStartedAt(),
                process.getCompletedAt(),
                process.getStages().stream()
                        .map(stage -> toStageDto(stage, decisionParticipantIds))
                        .toList()
        );
    }

    /** Все участники процесса — идентификаторы для пакетного запроса решений. */
    private static List<UUID> participantIds(ProcessInstance process) {
        return process.getStages().stream()
                .flatMap(stage -> stage.getIterations().stream())
                .flatMap(iteration -> iteration.getParticipants().stream())
                .map(Participant::getId)
                .toList();
    }

    /** Этап маршрута вместе со всеми его итерациями и участниками (вложенный вызов из {@link #toDto}). */
    public StageDto toStageDto(StageInstance stage, Set<UUID> decisionParticipantIds) {
        return new StageDto(
                stage.getId(),
                stage.getOrderIdx(),
                stage.getName(),
                stage.getStageType() == null ? null : stage.getStageType().name(),
                stage.getStatus(),
                stage.getDueAt(),
                stage.getDuration(),
                stage.getDecisionMode() != null ? stage.getDecisionMode().name() : null,
                stage.getIterations().stream()
                        .map(iteration -> toIterationDto(iteration, decisionParticipantIds))
                        .toList()
        );
    }

    /**
     * Итерация этапа. {@code dueDate} всегда {@code null} — у {@code StageIteration} нет поля
     * срока (см. описание класса).
     */
    public IterationDto toIterationDto(StageIteration iteration, Set<UUID> decisionParticipantIds) {
        return new IterationDto(
                iteration.getId(),
                iteration.getIterationIdx(),
                iteration.getStatus(),
                null, // StageIteration doesn't have dueDate field
                iteration.getParticipants().stream()
                        .map(participant -> toParticipantDto(participant, decisionParticipantIds))
                        .toList()
        );
    }

    /**
     * Участник этапа. {@code hasDecision} берётся из переданного набора, а не запросом на каждого
     * участника: прежняя реализация обращалась к БД по строке на страницу и на каждый этап
     * агрегата (N+1, замеры — {@code doc/LOAD_TESTING.md} §5).
     */
    public ParticipantDto toParticipantDto(Participant participant, Set<UUID> decisionParticipantIds) {
        return new ParticipantDto(
                participant.getId(),
                participant.getOrderIdx(),
                participant.getUserId(),
                participant.getOrganizationId(),
                participant.getStatus(),
                participant.getDueAt(),
                decisionParticipantIds.contains(participant.getId())
        );
    }

    /**
     * Пакетное преобразование для постраничного списка процессов. Каждый элемент проходит через
     * {@link #toDto}, поэтому список из N процессов даёт ту же ленивую загрузку, что и детальный
     * запрос (в {@code ProcessViewService.list} страница ограничена сверху).
     */
    public List<ProcessDto> toDtoList(List<ProcessInstance> processes) {
        return processes.stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Краткая карточка процесса для списков — {@link ProcessSummaryDto}: без этапов, итераций и
     * участников. Ради неё и появился параметр {@code view=summary}: полный агрегат на строку
     * страницы стоит десятки запросов к БД ({@code doc/LOAD_TESTING.md} §5.3).
     *
     * <p>Единственное вычисляемое поле — {@code currentStageName}: имя первого этапа в статусе
     * {@code Active}. У черновика, завершённого и отозванного процесса активного этапа нет, поэтому
     * поле остаётся {@code null}. Обращение к {@code process.getStages()} здесь ленивое, но
     * пакетное ({@code hibernate.default_batch_fetch_size}), то есть на страницу уходит один запрос,
     * а не запрос на строку.
     *
     * <p>{@code dueDate} не вычисляется: срока на уровне процесса в модели нет — в сервисе это
     * помечено TODO и здесь оставлено {@code null} осознанно, чтобы не выдумывать значение.
     */
    public ProcessSummaryDto toSummaryDto(ProcessInstance process) {
        String currentStageName = process.getStages().stream()
                .filter(stage -> "Active".equals(stage.getStatus()))
                .findFirst()
                .map(StageInstance::getName)
                .orElse(null);

        return new ProcessSummaryDto(
                process.getId(),
                process.getEntityType(),
                process.getEntityId(),
                process.getStatus(),
                process.getCreatedAt(),
                null, // срока процесса в модели нет — см. описание метода
                currentStageName
        );
    }
}
