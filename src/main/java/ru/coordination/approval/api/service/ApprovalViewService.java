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
        ProcessDto processDto = processMapper.toDto(process);

        List<StageWithDecisionsDto> stagesWithDecisions = process.getStages().stream()
                .map(stage -> {
                    List<ParticipantWithDecisionDto> participantsWithDecisions = stage.getIterations().stream()
                            .flatMap(iter -> iter.getParticipants().stream())
                            .map(p -> new ParticipantWithDecisionDto(
                                    processMapper.toParticipantDto(p),
                                    decisionMap.containsKey(p.getId()) ? decisionMapper.toDto(decisionMap.get(p.getId())) : null
                            ))
                            .toList();

                    return new StageWithDecisionsDto(
                            processMapper.toStageDto(stage),
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

        List<ProcessSummaryDto> summaries = processes.getContent().stream()
                .map(p -> {
                    String currentStageName = p.getStages().stream()
                            .filter(s -> "Active".equals(s.getStatus()))
                            .findFirst()
                            .map(StageInstance::getName)
                            .orElse(null);

                    return new ProcessSummaryDto(
                            p.getId(),
                            p.getEntityType(),
                            p.getEntityId(),
                            p.getStatus(),
                            p.getCreatedAt(),
                            null, // TODO: Calculate dueDate
                            currentStageName
                    );
                })
                .toList();

        return new PageImpl<>(summaries, pageable, processes.getTotalElements());
    }
}
