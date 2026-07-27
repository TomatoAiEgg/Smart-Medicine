package com.zhyf.order.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminOrderDetail(
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
        String validationStatus,
        String validationMessage,
        Instant validationCreatedAt,
        Instant createdAt,
        Instant updatedAt,
        List<Prescription> prescriptions
) {

    public record Prescription(
            UUID prescriptionId,
            String prescriptionNo,
            String externalPrescriptionNo,
            String prescriptionType,
            String prescriptionStatus,
            String doctorName,
            String diagnosis,
            int detailCount,
            Instant createdAt,
            List<DrugDetail> details
    ) {
    }

    public record DrugDetail(
            UUID detailId,
            String drugCode,
            String drugName,
            String platformDrugCode,
            String platformDrugName,
            String dose,
            String unit,
            String specialUsage,
            int sortNo,
            String batchNo,
            String validationTips,
            Instant createdAt
    ) {
    }
}
