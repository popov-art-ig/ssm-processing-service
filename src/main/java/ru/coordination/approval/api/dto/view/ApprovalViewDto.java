package ru.coordination.approval.api.dto.view;

import ru.coordination.approval.api.dto.comment.CommentDto;
import ru.coordination.approval.api.dto.process.ProcessDto;
import ru.coordination.approval.api.dto.remark.RemarkDto;
import java.util.List;

/**
 * Агрегированное представление согласования для UI (ADR-020; {@code 07_api_contract.md} §25.2):
 * всё, что нужно экрану, одним ответом — процесс, этапы с решениями, замечания и комментарии.
 *
 * <p>Отличие от {@link ru.coordination.approval.api.dto.process.ProcessDto}: решения и обсуждение
 * подгружены заранее, поэтому UI не делает по запросу на каждый этап. Собирается
 * {@code ApprovalViewService} batch-запросами, а не обходом связей в цикле.
 *
 * @param stages этапы вместе с участниками всех итераций и их решениями (см.
 *               {@link StageWithDecisionsDto}); дублирует {@code process.stages()}, но с решениями
 * @param remarks замечания процесса целиком, без разбивки по этапам и без вложений
 */
public record ApprovalViewDto(
        ProcessDto process,
        List<StageWithDecisionsDto> stages,
        List<RemarkDto> remarks,
        List<CommentDto> comments
) {
}
