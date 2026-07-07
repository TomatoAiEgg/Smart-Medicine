package com.zhyf.decoction.domain;

import java.time.Instant;
import java.util.UUID;

public record DecoctionTaskSnapshot(
        UUID taskId,
        String taskNo,
        UUID tenantId,
        UUID orderId,
        UUID prescriptionId,
        String orderNo,
        String prescriptionNo,
        String deviceCode,
        String pailNo,
        String taskStatus,
        String operator,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
