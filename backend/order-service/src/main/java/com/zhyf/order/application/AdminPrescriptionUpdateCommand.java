package com.zhyf.order.application;

public record AdminPrescriptionUpdateCommand(
        String prescriptionType,
        String hospitalType,
        Integer doseCount,
        Integer decoctionCount,
        Integer boilTimes,
        Integer isWithin,
        Integer perPackNum,
        Integer perPackDose,
        String medicationMethod,
        String medicationInstruction,
        String prescriptionRemark,
        String operator,
        String reason
) {
}
