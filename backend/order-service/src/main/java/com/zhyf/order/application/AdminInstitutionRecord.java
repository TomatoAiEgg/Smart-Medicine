package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminInstitutionRecord(
        UUID id,
        UUID tenantId,
        String institutionCode,
        String institutionName,
        String institutionType,
        String status,
        String storageType,
        Instant createdAt,
        Instant updatedAt
) {
}
