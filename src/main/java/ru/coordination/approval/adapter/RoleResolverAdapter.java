package ru.coordination.approval.adapter;

import java.util.List;
import java.util.UUID;
import ru.coordination.approval.dto.EntitySnapshot;

public interface RoleResolverAdapter {
    /**
     * Resolve role to list of user IDs.
     *
     * @param roleId role identifier
     * @param organizationId organization context
     * @param context entity snapshot for dynamic resolution
     * @return list of resolved user IDs
     */
    List<UUID> resolveRole(UUID roleId, UUID organizationId, EntitySnapshot context);
}
