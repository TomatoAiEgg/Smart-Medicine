package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminManualProcessItem(
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
        String patientNames,
        String hospitalTypes,
        String prescriptionTypes,
        String prescriptionNos,
        String externalPrescriptionNos,
        String doseCounts,
        Integer prescriptionCount,
        Instant deliveryTime,
        String orderRemark,
        Instant createdAt,
        Instant updatedAt
) {
}
