package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminOperatorRecord(
        UUID id,
        UUID tenantId,
        String username,
        String displayName,
        String roleCode,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
