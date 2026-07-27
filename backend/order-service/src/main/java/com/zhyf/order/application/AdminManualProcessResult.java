package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminManualProcessResult(
        UUID orderId,
        String orderNo,
        String fromStatus,
        String toStatus,
        int workflowTaskCount,
        int dispenseRecordCount,
        int decoctionTaskCount,
        String logisticsNo,
        boolean callbackSuppressed,
        Instant processedAt
) {
}
