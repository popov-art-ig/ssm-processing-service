package ru.coordination.approval.dto.view;

import ru.coordination.approval.dto.comment.CommentDto;
import ru.coordination.approval.dto.process.ProcessDto;
import ru.coordination.approval.dto.remark.RemarkDto;
import java.util.List;

public record ApprovalViewDto(
        ProcessDto process,
        List<StageWithDecisionsDto> stages,
        List<RemarkDto> remarks,
        List<CommentDto> comments
) {
}
