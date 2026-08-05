package com.zhyf.authinstitution.app;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AdminAuthRecords {

    private AdminAuthRecords() {
    }

    public record LoginCommand(String tenantCode, String username, String password, String requestId, String clientIp) {
    }

    public record RefreshCommand(String refreshToken, String requestId, String clientIp) {
    }

    public record AdminUserView(
            UUID userId,
            UUID tenantId,
            String tenantCode,
            String tenantName,
            String username,
            String displayName,
            List<String> roleCodes,
            List<UUID> institutionIds,
            List<String> permissions,
            boolean tenantWide
    ) {
    }

    public record LoginResult(
            String accessToken,
            String refreshToken,
            String tokenType,
            Instant expiresAt,
            Instant refreshExpiresAt,
            AdminUserView user
    ) {
    }
}
