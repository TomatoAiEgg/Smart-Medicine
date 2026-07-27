package com.zhyf.logistics.application;

import java.time.Instant;

public record LogisticsShipmentQuery(
        Instant startTime,
        Instant endTime,
        String institution,
        String orderNo,
        String patientName,
        String receiverName,
        String receiverPhone,
        String hospitalType,
        String status,
        String deliveryType,
        String logisticsCompany,
        String logisticsNo,
        int limit
) {
    public LogisticsShipmentQuery withLimit(int nextLimit) {
        return new LogisticsShipmentQuery(
                startTime,
                endTime,
                institution,
                orderNo,
                patientName,
                receiverName,
                receiverPhone,
                hospitalType,
                status,
                deliveryType,
                logisticsCompany,
                logisticsNo,
                nextLimit
        );
    }
}
