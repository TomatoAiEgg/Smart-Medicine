package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminOrderListItem(
        UUID orderId,
        UUID tenantId,
        UUID institutionId,
        String institutionName,
        String storageType,
        String orderNo,
        String externalOrderNo,
        String orderStatus,
        String patientName,
        String patientPhone,
        String receiverName,
        String receiverPhone,
        String receiverProvince,
        String receiverCity,
        String receiverZone,
        String receiverAddress,
        String addressType,
        String prescriptionNos,
        String externalPrescriptionNos,
        String prescriptionTypes,
        int prescriptionCount,
        int detailCount,
        String logisticsCompany,
        String logisticsNo,
        String logisticsStatus,
        Instant latestTraceTime,
        Instant createdAt,
        Instant updatedAt
) {
}
