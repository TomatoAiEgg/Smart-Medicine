package com.zhyf.order.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminHerbRecord(
        UUID id,
        UUID tenantId,
        String herbCode,
        String herbName,
        String drugSpecs,
        String drugOrigin,
        String unit,
        BigDecimal retailPrice,
        boolean enabled,
        String remark,
        Instant createdAt,
        Instant updatedAt
) {
}
