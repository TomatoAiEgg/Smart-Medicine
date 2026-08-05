package com.zhyf.order.application;

import java.util.List;
import java.util.UUID;

public record AdminRbacRoleCommand(
        String roleCode,
        String roleName,
        String dataScopeType,
        Boolean enabled,
        Integer version,
        List<String> permissionCodes,
        List<UUID> institutionIds
) {
}
