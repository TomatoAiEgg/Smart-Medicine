package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminOrderReviewItem(
        UUID orderId,
        UUID tenantId,
        UUID institutionId,
        String institutionName,
        String storageType,
        String orderNo,
        String externalOrderNo,
        String orderStatus,
        String receiverName,
        String receiverPhone,
        String receiverProvince,
        String receiverCity,
        String receiverZone,
        String receiverAddress,
        String addressType,
        Instant deliveryTime,
        Instant orderCreatedAt,
        String prescriptionNos,
        String externalPrescriptionNos,
        String hospitalTypes,
        String patientName,
        String patientPhone,
        String prescriptionTypes,
        String doseCounts,
        int prescriptionCount,
        String orderRemark,
        UUID reviewTaskId,
        String reviewTaskStatus,
        String reviewer,
        String reviewComment,
        Instant taskCreatedAt,
        Instant taskCompletedAt,
        Instant updatedAt
) {
}
