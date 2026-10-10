package ru.coordination.approval.api.dto.remark;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Тело запроса «отклонить исправление замечания» ({@code 07_api_contract.md}, группа 8).
 *
 * <p>По компонентам не отличается от {@link RejectRemarkRequest}: сервисный слой различает
 * операции по имени метода, а не по телу запроса, потому что второго поля «что именно отклоняется»
 * в модели нет.
 *
 * @param reason причина, по которой исправление не принято; сохранять её сейчас некуда — перехода
 *               для этого сценария в карте замечания нет
 */
public record RejectResolutionRequest(
        @NotBlank String reason,
        @NotNull UUID actorId
) {
}
