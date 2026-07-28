package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminDictTypeRecord(
        UUID id,
        UUID tenantId,
        String typeCode,
        String typeName,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
