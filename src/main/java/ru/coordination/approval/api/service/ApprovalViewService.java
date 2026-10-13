package ru.coordination.approval.api.service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.coordination.approval.api.dto.comment.CommentDto;
import ru.coordination.approval.api.dto.decision.DecisionDto;
import ru.coordination.approval.api.dto.process.ProcessDto;
import ru.coordination.approval.api.dto.remark.RemarkDto;
import ru.coordination.approval.api.dto.view.*;
import ru.coordination.approval.api.exception.EntityNotFoundException;
import ru.coordination.approval.api.mapper.*;
import ru.coordination.approval.domain.process.*;

/**
 * Read-model для интерфейса согласования: собирает агрегированные представления, которые не
 * совпадают ни с одной доменной сущностью (ADR-020, {@code 07_api_contract.md} §12).
 *
 * <p>Все методы — только чтение ({@code readOnly = true}) и возвращают DTO, а не сущности:
 * представление для UI склеивает процесс, этапы, итерации, участников, решения, замечания и
 * комментарии, поэтому отдать одну сущность невозможно.
 *
 * <p><b>Известные ограничения.</b> Данные загружаются несколькими запросами и склеиваются в
 * памяти (это осознанный компромисс для read-model, но на большом процессе даёт заметный объём
 * выборки); {@code dueDate} в {@link TaskDto} и сводке процесса не вычисляется — в коде оставлен
 * {@code null} с пометкой TODO; при отсутствии процесса для сущности бросается
 * {@link EntityNotFoundException} (404).
 */
@Service
@RequiredArgsConstructor
public class ApprovalViewService {

    private final ProcessRepository processRepository;
    private final ParticipantRepository participantRepository;
    private final DecisionRepository decisionRepository;
    private final RemarkRepository remarkRepository;
    private final CommentRepository commentRepository;
    private final ProcessMapper processMapper;
    private final RemarkMapper remarkMapper;
    private final CommentMapper commentMapper;
    private final DecisionMapper decisionMapper;

    /**
     * Агрегированное представление согласования по сущности-владельцу процесса: процесс, этапы
     * с участниками и их решениями, замечания и комментарии. Один вызов закрывает потребности
     * карточки согласования в UI, чтобы фронтенд не собирал её из десятка запросов.
     *
     * @param entityType тип сущности-владельца (например {@code DOCUMENT})
     * @param entityId   идентификатор сущности-владельца
     * @throws EntityNotFoundException если по паре ({@code entityType}, {@code entityId}) процесса нет
     */
    @Transactional(readOnly = true)
    public ApprovalViewDto getApprovalView(String entityType, UUID entityId) {
        // 1. Find process with fetch join
        ProcessInstance process = processRepository.findByEntityTypeAndEntityId(entityType, entityId)
                .orElseThrow(() -> new EntityNotFoundException("Process not found for entity: " + entityType + "/" + entityId));

        // 2. Load all related data
        List<UUID> iterationIds = process.getStages().stream()
                .flatMap(s -> s.getIterations().stream())
                .map(StageIteration::getId)
                .toList();

        List<Participant> participants = participantRepository.findByStageIterationIdIn(iterationIds);

        List<UUID> participantIds = participants.stream()
                .map(Participant::getId)
                .toList();

        List<Decision> decisions = decisionRepository.findByParticipantIdIn(participantIds);
        Map<UUID, Decision> decisionMap = decisions.stream()
                .collect(Collectors.toMap(d -> d.getParticipant().getId(), d -> d));

        List<Remark> remarks = remarkRepository.findByProcessId(process.getId());
        List<Comment> comments = commentRepository.findByProcessId(process.getId());

        // 3. Build DTOs
        // Набор участников с решениями уже посчитан пакетным запросом выше, поэтому маппер
        // получает его готовым: иначе каждый участник стоил бы отдельного запроса (N+1).
        Set<UUID> decisionParticipantIds = decisionMap.keySet();
        ProcessDto processDto = processMapper.toDto(process, decisionParticipantIds);

        List<StageWithDecisionsDto> stagesWithDecisions = process.getStages().stream()
                .map(stage -> {
                    List<ParticipantWithDecisionDto> participantsWithDecisions = stage.getIterations().stream()
                            .flatMap(iter -> iter.getParticipants().stream())
                            .map(p -> new ParticipantWithDecisionDto(
                                    processMapper.toParticipantDto(p, decisionParticipantIds),
                                    decisionMap.containsKey(p.getId()) ? decisionMapper.toDto(decisionMap.get(p.getId())) : null
                            ))
                            .toList();

                    return new StageWithDecisionsDto(
                            processMapper.toStageDto(stage, decisionParticipantIds),
                            participantsWithDecisions
                    );
                })
                .toList();

        List<RemarkDto> remarkDtos = remarks.stream()
                .map(remarkMapper::toDto)
                .toList();

        List<CommentDto> commentDtos = comments.stream()
                .map(commentMapper::toDto)
                .toList();

        return new ApprovalViewDto(processDto, stagesWithDecisions, remarkDtos, commentDtos);
    }

    /**
     * Задачи пользователя: его участия в статусе {@code Assigned} по всем процессам.
     *
     * <p>Отбор идёт по {@code participant.status}, а не по статусу процесса: задача исчезает из
     * списка, как только участник принял решение. Пагинация применяется на уровне запроса
     * участников, поэтому {@code totalElements} — число задач, а не страниц.
     *
     * @param userId   идентификатор участника
     * @param pageable страница и сортировка
     * @return страница {@link TaskDto}; пустая, если у пользователя нет назначенных задач
     */
    @Transactional(readOnly = true)
    public Page<TaskDto> getMyTasks(UUID userId, Pageable pageable) {
        Page<Participant> participants = participantRepository.findByUserIdAndStatus(userId, "Assigned", pageable);

        List<TaskDto> tasks = participants.getContent().stream()
                .map(p -> {
                    StageIteration iteration = p.getStageIteration();
                    StageInstance stage = iteration.getStage();
                    ProcessInstance process = stage.getProcess();

                    return new TaskDto(
                            p.getId(),
                            process.getId(),
                            stage.getId(),
                            p.getId(),
                            process.getEntityType(),
                            process.getEntityId(),
                            stage.getName(),
                            p.getDueAt(),
                            p.getStatus()
                    );
                })
                .toList();

        return new PageImpl<>(tasks, pageable, participants.getTotalElements());
    }

    /**
     * «Мои процессы»: процессы, где пользователь инициатор или ответственный, с необязательным
     * фильтром по статусу.
     *
     * <p>Текущий этап сводки определяется первым этапом в статусе {@code Active} — если активного
     * этапа нет (черновик, завершённый или отозванный процесс), поле остаётся {@code null}.
     *
     * @param userId   идентификатор пользователя (сравнивается и с инициатором, и с ответственным)
     * @param status   фильтр по статусу процесса; {@code null} — без фильтра
     * @param pageable страница и сортировка
     * @return страница {@link ProcessSummaryDto} — краткая карточка без этапов и участников
     */
    @Transactional(readOnly = true)
    public Page<ProcessSummaryDto> getMyProcesses(UUID userId, String status, Pageable pageable) {
        Page<ProcessInstance> processes;

        if (status != null) {
            processes = processRepository.findByInitiatorIdOrResponsibleUserIdAndStatus(
                    userId, userId, status, pageable);
        } else {
            processes = processRepository.findByInitiatorIdOrResponsibleUserId(
                    userId, userId, pageable);
        }

        // Маппинг краткой карточки общий с view=summary у списка процессов: две реализации одного
        // DTO разошлись бы при первой же правке (например, когда появится расчёт dueDate).
        return processes.map(processMapper::toSummaryDto);
    }
}