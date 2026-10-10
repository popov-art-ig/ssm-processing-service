package ru.coordination.approval.domain.registry;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Составной первичный ключ {@link StatusRegistry} — {@code (code, entity_type)}.
 *
 * <p>Один и тот же код статуса осмысленно повторяется для разных агрегатов ({@code InProgress}
 * у PROCESS и REMARK, {@code Draft} у PROCESS и REMARK, {@code Active} у STAGE и REMARK), поэтому
 * в схеме (миграция {@code V27__status_registry_composite_key.sql}) ключ составной.
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class StatusRegistryId implements Serializable {

    private String code;
    private String entityType;
}
