package com.zhyf.order.application;

import java.math.BigDecimal;
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
        Instant deliveryTime,
        String batchNo,
        String orderRemark,
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
            String hospitalType,
            Integer doseCount,
            Integer decoctionCount,
            Integer boilTimes,
            Integer isWithin,
            Integer perPackNum,
            Integer perPackDose,
            BigDecimal decoctionUnitPrice,
            BigDecimal decoctionTotalPrice,
            BigDecimal totalAmount,
            String doctorName,
            String diagnosis,
            String departmentName,
            String wardName,
            String bedNo,
            String medicationMethod,
            String medicationInstruction,
            String prescriptionRemark,
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
            String drugSpecs,
            String drugOrigin,
            String dose,
            String unit,
            String specialUsage,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal settlementUnitPrice,
            BigDecimal totalPrice,
            BigDecimal settlementTotalPrice,
            int sortNo,
            String batchNo,
            String remark,
            String validationTips,
            Instant createdAt
    ) {
    }
}
