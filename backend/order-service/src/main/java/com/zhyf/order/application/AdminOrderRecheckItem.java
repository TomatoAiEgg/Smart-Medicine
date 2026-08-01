package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminOrderRecheckItem(
        UUID orderId,
        UUID tenantId,
        UUID institutionId,
        UUID prescriptionId,
        String institutionName,
        String storageType,
        String orderNo,
        String externalOrderNo,
        String orderStatus,
        String prescriptionNo,
        String externalPrescriptionNo,
        String prescriptionType,
        String hospitalType,
        Integer isWithin,
        Integer doseCount,
        String patientName,
        String patientPhone,
        String addressType,
        String batchNo,
        Instant deliveryTime,
        Instant orderCreatedAt,
        Instant dispensedAt,
        String dispenser,
        Instant recheckedAt,
        String rechecker,
        String pailNos,
        String orderRemark,
        Instant updatedAt
) {
}
