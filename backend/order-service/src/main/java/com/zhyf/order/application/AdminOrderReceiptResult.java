package com.zhyf.order.application;

import java.time.Instant;

public record AdminOrderReceiptResult(
        String orderNo,
        String fromStatus,
        String toStatus,
        boolean success,
        String message,
        Instant signedAt
) {
}
