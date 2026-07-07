package com.zhyf.order.domain;

import java.time.Instant;
import java.util.UUID;

public record OrderSnapshot(
        UUID orderId,
        UUID tenantId,
        UUID institutionId,
        String orderNo,
        String externalOrderNo,
        String status,
        Instant createdAt
) {
}
