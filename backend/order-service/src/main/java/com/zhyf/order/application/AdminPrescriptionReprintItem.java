package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminPrescriptionReprintItem(
        UUID orderId,
        UUID prescriptionId,
        String orderNo,
        String externalOrderNo,
        String orderStatus,
        String prescriptionNo,
        String externalPrescriptionNo,
        String prescriptionStatus,
        String institutionName,
        String patientName,
        String patientPhone,
        String receiverProvince,
        String receiverCity,
        String receiverZone,
        String receiverAddress,
        String addressType,
        Instant deliveryTime,
        Instant createdAt,
        String hospitalType,
        String prescriptionType,
        Integer isWithin,
        Integer doseCount,
        String batchNo,
        String dispenser
) {
}
