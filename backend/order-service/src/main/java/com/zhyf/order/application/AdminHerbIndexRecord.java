package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminHerbIndexRecord(
        UUID id,
        UUID tenantId,
        UUID institutionId,
        String institutionCode,
        String institutionName,
        String externalHerbCode,
        String externalHerbName,
        UUID herbId,
        String herbCode,
        String herbName,
        String matchType,
        boolean enabled,
        String remark,
        Instant createdAt,
        Instant updatedAt
) {
}
