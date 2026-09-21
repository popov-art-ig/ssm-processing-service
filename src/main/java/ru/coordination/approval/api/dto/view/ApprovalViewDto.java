package ru.coordination.approval.api.dto.view;

import ru.coordination.approval.api.dto.comment.CommentDto;
import ru.coordination.approval.api.dto.process.ProcessDto;
import ru.coordination.approval.api.dto.remark.RemarkDto;

import java.util.List;

public record ApprovalViewDto(
        ProcessDto process,
        List<StageWithDecisionsDto> stages,
        List<RemarkDto> remarks,
        List<CommentDto> comments
) {
}
