package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminHerbAreaRecord(
        UUID id,
        UUID tenantId,
        String areaCode,
        String areaName,
        boolean enabled,
        String remark,
        Instant createdAt,
        Instant updatedAt
) {
}
