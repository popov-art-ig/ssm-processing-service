package ru.coordination.approval.api.mapper;

import org.springframework.stereotype.Component;
import ru.coordination.approval.api.dto.remark.RemarkDto;
import ru.coordination.approval.domain.process.Remark;

/**
 * Преобразование {@code Remark} в {@link RemarkDto}.
 *
 * <p>Маппер обращается к ленивым связям {@code process} и {@code stage} — работает только внутри
 * открытой сессии. Что не переносится: {@code severity} и {@code resolutionText} в ответе всегда
 * {@code null} (в {@code remark} таких полей нет), а обратная потеря — {@code rejectionReason},
 * {@code rejectedAt}, {@code activatedAt}/{@code activatedBy} и вложения: в сущности они есть, в
 * API не выводятся, поэтому причину отклонения замечания через REST не увидеть.
 */
@Component
public class RemarkMapper {

    /**
     * Представление замечания. Обращается к ленивым связям {@code process} и {@code stage} —
     * только внутри открытой сессии; поля, которых нет в сущности, отдаются как {@code null}
     * (см. описание класса).
     */
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
