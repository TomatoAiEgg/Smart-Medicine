package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminOrderReceiptItem(
        UUID orderId,
        UUID tenantId,
        String orderNo,
        String externalOrderNo,
        String institutionName,
        String receiverName,
        String receiverPhone,
        String receiverProvince,
        String receiverCity,
        String receiverZone,
        String receiverAddress,
        String patientName,
        String prescriptionTypes,
        String orderStatus,
        String logisticsCompany,
        String logisticsNo,
        String logisticsStatus,
        Instant createdAt,
        Instant updatedAt
) {
}
