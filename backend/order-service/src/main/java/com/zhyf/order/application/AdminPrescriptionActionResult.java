package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminPrescriptionActionResult(
        UUID orderId,
        String orderNo,
        UUID prescriptionId,
        String prescriptionNo,
        String fromPrescriptionStatus,
        String toPrescriptionStatus,
        boolean orderStatusChanged,
        String fromOrderStatus,
        String toOrderStatus,
        String eventId,
        Instant operatedAt
) {
}
