package ru.coordination.approval.api.mapper;

import org.springframework.stereotype.Component;
import ru.coordination.approval.api.dto.remark.RemarkDto;
import ru.coordination.approval.domain.process.Remark;

@Component
public class RemarkMapper {

    public RemarkDto toDto(Remark remark) {
        return new RemarkDto(
                remark.getId(),
                remark.getProcess().getId(),
                remark.getStage() != null ? remark.getStage().getId() : null,
                remark.getText(),
                null, // severity field doesn't exist in Remark
                remark.getStatus(),
                remark.getAuthorId(),
                remark.getCreatedAt(),
                null, // resolutionText field doesn't exist in Remark
                remark.getResolvedAt()
        );
    }
}
