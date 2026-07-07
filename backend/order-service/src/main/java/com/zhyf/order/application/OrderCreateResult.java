package com.zhyf.order.application;

import java.util.UUID;

public record OrderCreateResult(
        UUID orderId,
        String orderNo,
        String externalOrderNo,
        String status,
        boolean duplicated
) {
}
