package ru.coordination.approval.adapter;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.coordination.approval.dto.EntitySnapshot;

/**
 * Stub implementation of RoleResolverAdapter.
 * In real system, this would resolve roles from external IAM/user management system.
 */
@Component
public class StubRoleResolverAdapter implements RoleResolverAdapter {

    @Override
    public List<UUID> resolveRole(UUID roleId, UUID organizationId, EntitySnapshot context) {
        // Stub: return empty list
        // In real implementation, this would call external system to get users by role
        return List.of();
    }
}
