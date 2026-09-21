package ru.coordination.approval.adapter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.coordination.approval.dto.EntitySnapshot;

/**
 * Stub implementation of EntityAdapter.
 * In real system, this would fetch entity data from external systems.
 */
@Component
public class EntityAdapter {

    public EntitySnapshot fetchEntitySnapshot(String entityType, UUID entityId) {
        // Stub: return empty snapshot
        return new EntitySnapshot(
                entityType,
                null,
                entityId,
                Map.of(),
                Instant.now()
        );
    }
}
