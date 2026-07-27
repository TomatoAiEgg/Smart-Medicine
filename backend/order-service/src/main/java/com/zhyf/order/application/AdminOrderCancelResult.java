package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminOrderCancelResult(
        UUID orderId,
        String orderNo,
        String fromStatus,
        String toStatus,
        int cancelledPrescriptionCount,
        int cancelledWorkflowTaskCount,
        Instant cancelledAt
) {
}
