package com.zhyf.order.application;

import java.time.Instant;

public record AdminOrderSearchQuery(
        Instant startTime,
        Instant endTime,
        String institution,
        String prescriptionType,
        String hospitalType,
        String orderStatus,
        String excludeOrderStatus,
        String decoctionCenter,
        String deliveryType,
        String logisticsCompany,
        String province,
        String keyword,
        String hospitalPrescriptionNo,
        String patientName,
        String receiverPhone,
        int page,
        int pageSize
) {
}
