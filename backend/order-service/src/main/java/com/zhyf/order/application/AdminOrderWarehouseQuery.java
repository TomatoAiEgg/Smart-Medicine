package com.zhyf.order.application;

import java.time.Instant;

public record AdminOrderWarehouseQuery(
        Instant startTime,
        Instant endTime,
        String institution,
        String prescriptionType,
        String hospitalType,
        String orderStatus,
        String decoctionCenter,
        String deliveryType,
        String logisticsCompany,
        String province,
        String orderNo,
        String prescriptionNo,
        String hospitalPrescriptionNo,
        String patientName,
        String receiverPhone,
        String nodeTime,
        int page,
        int pageSize
) {
}
