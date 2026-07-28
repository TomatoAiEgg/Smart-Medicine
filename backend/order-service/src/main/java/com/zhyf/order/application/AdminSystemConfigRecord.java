package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminSystemConfigRecord(
        UUID id,
        UUID tenantId,
        String configKey,
        String configName,
        String configValue,
        String valueType,
        boolean enabled,
        String remark,
        Instant createdAt,
        Instant updatedAt
) {
}
