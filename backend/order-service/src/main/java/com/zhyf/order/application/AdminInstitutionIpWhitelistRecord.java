package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminInstitutionIpWhitelistRecord(
        UUID id,
        UUID tenantId,
        UUID institutionId,
        String institutionCode,
        String institutionName,
        String institutionType,
        String ipRange,
        boolean enabled,
        Instant createdAt
) {
}
