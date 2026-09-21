package ru.coordination.approval.adapter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.coordination.approval.dto.EntitySnapshot;

@Component
public class EntityAdapterStub implements EntityAdapter {

    @Override
    public EntitySnapshot fetchEntitySnapshot(String entityType, UUID entityId) {
        // Stub for tests
        return new EntitySnapshot(
                entityType,
                null,
                entityId,
                Map.of("amount", 1000000, "priority", "HIGH"),
                Instant.now()
        );
    }
}
