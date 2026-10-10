package ru.coordination.approval.adapter;

import java.util.UUID;
import ru.coordination.approval.dto.generation.EntitySnapshot;

/**
 * Adapter for fetching entity data from external systems.
 */
public interface EntityAdapter {

    EntitySnapshot fetchEntitySnapshot(String entityType, UUID entityId);
}
