package ru.coordination.approval.adapter;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.coordination.approval.dto.EntitySnapshot;

@Component
public class RoleResolverAdapterStub implements RoleResolverAdapter {

    @Override
    public List<UUID> resolveRole(UUID roleId, UUID organizationId, EntitySnapshot context) {
        // Stub: always returns empty list
        // Real implementation will be in PHASE-20
        return List.of();
    }
}
