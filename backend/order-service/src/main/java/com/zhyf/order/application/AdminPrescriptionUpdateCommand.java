package com.zhyf.order.application;

public record AdminPrescriptionUpdateCommand(
        String prescriptionType,
        String hospitalType,
        Integer doseCount,
        Integer decoctionCount,
        String medicationMethod,
        String medicationInstruction,
        String prescriptionRemark,
        String operator,
        String reason
) {
}
