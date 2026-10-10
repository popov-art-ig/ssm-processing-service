package ru.coordination.approval.api.mapper;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.coordination.approval.api.dto.process.IterationDto;
import ru.coordination.approval.api.dto.process.ParticipantDto;
import ru.coordination.approval.api.dto.process.ProcessDto;
import ru.coordination.approval.api.dto.process.StageDto;
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
 *   <li>{@code ParticipantDto.hasDecision} вычисляется запросом к {@code decision} на каждого
 *       участника — страница из N участников даёт N дополнительных запросов (N+1);</li>
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
     */
    public ProcessDto toDto(ProcessInstance process) {
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
                        .map(this::toStageDto)
                        .toList()
        );
    }

    /** Этап маршрута вместе со всеми его итерациями и участниками (вложенный вызов из {@link #toDto}). */
    public StageDto toStageDto(StageInstance stage) {
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
                        .map(this::toIterationDto)
                        .toList()
        );
    }

    /**
     * Итерация этапа. {@code dueDate} всегда {@code null} — у {@code StageIteration} нет поля
     * срока (см. описание класса).
     */
    public IterationDto toIterationDto(StageIteration iteration) {
        return new IterationDto(
                iteration.getId(),
                iteration.getIterationIdx(),
                iteration.getStatus(),
                null, // StageIteration doesn't have dueDate field
                iteration.getParticipants().stream()
                        .map(this::toParticipantDto)
                        .toList()
        );
    }

    /**
     * Участник этапа. Обращается к {@link DecisionMapper#hasDecision} на каждого участника —
     * отдельный запрос на строку (N+1, см. описание класса).
     */
    public ParticipantDto toParticipantDto(Participant participant) {
        Boolean hasDecision = decisionMapper.hasDecision(participant);
        return new ParticipantDto(
                participant.getId(),
                participant.getOrderIdx(),
                participant.getUserId(),
                participant.getOrganizationId(),
                participant.getStatus(),
                participant.getDueAt(),
                hasDecision
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
}
