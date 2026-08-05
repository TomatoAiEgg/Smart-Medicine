package com.zhyf.order.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminRbacRoleRecord(
        UUID id,
        UUID tenantId,
        String roleCode,
        String roleName,
        String dataScopeType,
        boolean builtIn,
        boolean enabled,
        int version,
        long operatorCount,
        List<String> permissionCodes,
        List<UUID> institutionIds,
        Instant createdAt,
        Instant updatedAt
) {
}
