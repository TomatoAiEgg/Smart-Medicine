package com.zhyf.common.security;

import java.util.List;
import java.util.UUID;

public record AdminRequestContext(
        UUID userId,
        String username,
        UUID tenantId,
        List<String> roleCodes,
        List<UUID> institutionIds,
        List<String> permissions,
        boolean tenantWide
) {

    public AdminRequestContext {
        roleCodes = List.copyOf(roleCodes == null ? List.of() : roleCodes);
        institutionIds = List.copyOf(institutionIds == null ? List.of() : institutionIds);
        permissions = List.copyOf(permissions == null ? List.of() : permissions);
    }
}
