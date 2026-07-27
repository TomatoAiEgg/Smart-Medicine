package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminOrderWarehouseItem(
        UUID orderId,
        UUID tenantId,
        String orderNo,
        String externalOrderNo,
        String orderStatus,
        Instant createdAt,
        String batchNo,
        String institutionName,
        String storageType,
        String addressType,
        String receiverName,
        String receiverPhone,
        Instant deliveryTime,
        String receiverProvince,
        String receiverCity,
        String receiverZone,
        String receiverAddress,
        String hospitalTypes,
        String patientName,
        String patientAge,
        String departmentNames,
        String prescriptionTypes,
        String prescriptionNos,
        String externalPrescriptionNos,
        String doseCounts,
        String perPackNums,
        String perPackDoses,
        String logisticsCompany,
        String logisticsNo
) {
}
