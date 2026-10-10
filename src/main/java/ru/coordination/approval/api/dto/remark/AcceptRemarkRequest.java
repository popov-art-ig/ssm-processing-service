package ru.coordination.approval.api.dto.remark;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Тело запроса «принять исправление замечания» ({@code 07_api_contract.md}, группа 8). Несёт
 * только актора: отдельного комментария к приёмке контракт не описывает.
 *
 * <p>Используется заглушкой {@code RemarkController#acceptRemark}, и {@code actorId} здесь не
 * просто не сохраняется — сам переход в статус «принято» в карте {@code RemarkStateMachine} не
 * зарегистрирован (V24).
 */
public record AcceptRemarkRequest(
        @NotNull UUID actorId
) {
}
