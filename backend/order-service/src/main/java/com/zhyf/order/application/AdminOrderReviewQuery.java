package com.zhyf.order.application;

import java.time.Instant;

public record AdminOrderReviewQuery(
        Instant startTime,
        Instant endTime,
        String institution,
        String prescriptionType,
        String hospitalType,
        Integer isWithin,
        String deliveryType,
        String reviewStatus,
        String orderNo,
        String prescriptionNo,
        String hospitalPrescriptionNo,
        String patientName,
        String doseRange,
        int page,
        int pageSize
) {
}
