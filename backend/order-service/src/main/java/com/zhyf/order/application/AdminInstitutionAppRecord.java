package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminInstitutionAppRecord(
        UUID id,
        UUID tenantId,
        UUID institutionId,
        String institutionCode,
        String institutionName,
        String institutionType,
        String appKey,
        String signType,
        String callbackUrl,
        boolean enabled,
        boolean appSecretConfigured,
        Instant createdAt,
        Instant updatedAt
) {
}
