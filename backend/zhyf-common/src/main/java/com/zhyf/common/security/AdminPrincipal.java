package com.zhyf.common.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminPrincipal(
        UUID userId,
        UUID tenantId,
        String tenantCode,
        String tenantName,
        String username,
        String displayName,
        List<String> roleCodes,
        List<UUID> institutionIds,
        List<String> permissions,
        Instant issuedAt,
        Instant expiresAt,
        String tokenId
) {

    public AdminPrincipal {
        roleCodes = List.copyOf(roleCodes == null ? List.of() : roleCodes);
        institutionIds = List.copyOf(institutionIds == null ? List.of() : institutionIds);
        permissions = List.copyOf(permissions == null ? List.of() : permissions);
    }
}
