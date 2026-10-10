package ru.coordination.approval.api.mapper;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import ru.coordination.approval.api.dto.template.ApplicabilityRuleDto;
import ru.coordination.approval.api.dto.template.CreateTemplateRequest;
import ru.coordination.approval.api.dto.template.SlotTemplateDto;
import ru.coordination.approval.api.dto.template.StageTemplateDto;
import ru.coordination.approval.api.dto.template.UpdateTemplateRequest;
import ru.coordination.approval.domain.common.DecisionMode;
import ru.coordination.approval.domain.common.ExecutionOrder;
import ru.coordination.approval.domain.common.ProcessType;
import ru.coordination.approval.domain.common.StageType;

/**
 * Преобразование DTO шаблона из представления API в DTO сервисного слоя.
 *
 * <p>Причина разделения: в API перечисления передаются строками (`"STANDARD"`, `"APPROVAL"`),
 * чтобы клиент получал понятную ошибку на неверное значение и не зависел от порядка констант, а
 * {@code TemplateService} работает с типизированными enum-ами
 * ({@link ru.coordination.approval.service.dto.TemplateCreateRequest}).
 *
 * <p>Неверное значение перечисления — это ошибка клиента, поэтому бросается
 * {@link IllegalArgumentException} (обработчик отдаёт {@code 400 BAD_REQUEST}), а не 500.
 */
@Component
public class TemplateRequestMapper {

    public ru.coordination.approval.service.dto.TemplateCreateRequest toServiceRequest(
            CreateTemplateRequest request) {

        return new ru.coordination.approval.service.dto.TemplateCreateRequest(
                request.name(),
                parseEnum(request.processType(), ProcessType.class, "processType"),
                request.processIterationEnabled(),
                request.responsibleRoles(),
                request.requiresResponsibleApproval(),
                toServiceStages(request.stages()),
                toServiceRules(request.applicabilityRules()),
                request.createdBy());
    }

    public ru.coordination.approval.service.dto.TemplateUpdateRequest toServiceRequest(
            UpdateTemplateRequest request) {

        return new ru.coordination.approval.service.dto.TemplateUpdateRequest(
                request.name(),
                request.processIterationEnabled(),
                request.responsibleRoles(),
                request.requiresResponsibleApproval(),
                toServiceStages(request.stages()),
                toServiceRules(request.applicabilityRules()));
    }

    private List<ru.coordination.approval.service.dto.StageTemplateDto> toServiceStages(
            List<StageTemplateDto> stages) {

        if (stages == null) {
            return null;
        }
        return stages.stream().map(this::toServiceStage).toList();
    }

    private ru.coordination.approval.service.dto.StageTemplateDto toServiceStage(StageTemplateDto stage) {
        return new ru.coordination.approval.service.dto.StageTemplateDto(
                stage.orderIdx(),
                stage.name(),
                stage.description(),
                parseEnum(stage.stageType(), StageType.class, "stageType"),
                stage.duration(),
                parseEnum(stage.decisionMode(), DecisionMode.class, "decisionMode"),
                parseEnum(stage.executionOrder(), ExecutionOrder.class, "executionOrder"),
                stage.isMandatory(),
                stage.isOrderMandatory(),
                stage.allowedReturnStages(),
                toServiceSlots(stage.actorSlots()));
    }

    private List<ru.coordination.approval.service.dto.SlotTemplateDto> toServiceSlots(
            List<SlotTemplateDto> slots) {

        if (slots == null) {
            return null;
        }
        return slots.stream()
                .map(slot -> new ru.coordination.approval.service.dto.SlotTemplateDto(
                        slot.orderIdx(),
                        slot.userId(),
                        slot.organizationId(),
                        slot.acceptableRoles(),
                        slot.required(),
                        slot.isUserEditable(),
                        slot.isOrganizationEditable(),
                        slot.isDeletable()))
                .toList();
    }

    private List<ru.coordination.approval.service.dto.ApplicabilityRuleDto> toServiceRules(
            List<ApplicabilityRuleDto> rules) {

        if (rules == null) {
            return null;
        }
        return rules.stream()
                .map(rule -> new ru.coordination.approval.service.dto.ApplicabilityRuleDto(
                        rule.ruleIdx(),
                        rule.entityTypes(),
                        rule.entitySubtypes(),
                        rule.attributeConditions()))
                .toList();
    }

    /**
     * Разбирает значение перечисления без учёта регистра и пробелов по краям.
     *
     * @param value        значение из запроса; {@code null} допустим — перечисление опционально
     * @param enumType     целевой тип
     * @param fieldName    имя поля для сообщения об ошибке
     */
    static <E extends Enum<E>> E parseEnum(String value, Class<E> enumType, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (E constant : enumType.getEnumConstants()) {
            if (constant.name().equals(normalized)) {
                return constant;
            }
        }

        throw new IllegalArgumentException(
                "Unknown %s '%s'; expected one of %s".formatted(
                        fieldName, value, List.of(enumType.getEnumConstants())));
    }
}
