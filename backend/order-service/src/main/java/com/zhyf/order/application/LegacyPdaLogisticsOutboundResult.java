package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record LegacyPdaLogisticsOutboundResult(
        UUID orderId,
        String orderNo,
        String fromStatus,
        String toStatus,
        String logisticsNo,
        Instant outboundAt
) {
}
