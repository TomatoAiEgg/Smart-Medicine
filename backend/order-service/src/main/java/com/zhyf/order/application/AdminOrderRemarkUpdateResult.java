package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminOrderRemarkUpdateResult(
        UUID orderId,
        String orderNo,
        String orderRemark,
        Instant updatedAt
) {
}
