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

@Component
@RequiredArgsConstructor
public class ProcessMapper {

    private final DecisionMapper decisionMapper;

    public ProcessDto toDto(ProcessInstance process) {
        return new ProcessDto(
                process.getId(),
                process.getEntityType(),
                process.getEntitySubtype(),
                process.getEntityId(),
                process.getProcessType().name(),
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

    public StageDto toStageDto(StageInstance stage) {
        return new StageDto(
                stage.getId(),
                stage.getOrderIdx(),
                stage.getName(),
                stage.getStageType().name(),
                stage.getStatus(),
                stage.getDueAt(),
                stage.getDuration(),
                stage.getDecisionMode() != null ? stage.getDecisionMode().name() : null,
                stage.getIterations().stream()
                        .map(this::toIterationDto)
                        .toList()
        );
    }

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

    public List<ProcessDto> toDtoList(List<ProcessInstance> processes) {
        return processes.stream()
                .map(this::toDto)
                .toList();
    }
}
