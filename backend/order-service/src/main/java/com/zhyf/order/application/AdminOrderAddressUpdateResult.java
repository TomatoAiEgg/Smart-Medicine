package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminOrderAddressUpdateResult(
        UUID orderId,
        String orderNo,
        String receiverName,
        String receiverPhone,
        String receiverProvince,
        String receiverCity,
        String receiverZone,
        String receiverAddress,
        String addressType,
        Instant deliveryTime,
        Instant updatedAt
) {
}
