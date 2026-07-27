package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminOrderInitializeResult(
        UUID orderId,
        String orderNo,
        String fromStatus,
        String toStatus,
        int resetPrescriptionCount,
        int cancelledWorkflowTaskCount,
        int cancelledDecoctionTaskCount,
        int deletedShipmentCount,
        String eventId,
        Instant initializedAt
) {
}
