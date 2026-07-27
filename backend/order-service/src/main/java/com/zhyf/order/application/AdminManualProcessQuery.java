package com.zhyf.order.application;

import java.time.Instant;

public record AdminManualProcessQuery(
        Instant startTime,
        Instant endTime,
        String institution,
        String prescriptionType,
        String hospitalType,
        Integer isWithin,
        String processType,
        String deliveryType,
        String orderNo,
        String prescriptionNo,
        String hospitalPrescriptionNo,
        String patientName,
        String doseRange,
        int page,
        int pageSize
) {
}
