package com.zhyf.order.application;

import java.util.UUID;

public record OrderStatusUpdateResult(
        UUID orderId,
        String orderNo,
        String fromStatus,
        String toStatus
) {
}
