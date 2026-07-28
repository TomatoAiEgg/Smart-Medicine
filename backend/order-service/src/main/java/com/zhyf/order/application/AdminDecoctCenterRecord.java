package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminDecoctCenterRecord(
        UUID id,
        UUID tenantId,
        String centerCode,
        String centerName,
        String contactName,
        String contactPhone,
        String address,
        boolean enabled,
        String remark,
        Instant createdAt,
        Instant updatedAt
) {
}
