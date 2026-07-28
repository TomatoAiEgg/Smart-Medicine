package com.zhyf.order.application;

import java.util.UUID;

public record AdminOrderMergeCandidate(
        UUID tenantId,
        UUID orderId,
        String orderNo
) {
}
