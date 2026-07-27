package com.zhyf.logistics.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class LogisticsRecords {

    private LogisticsRecords() {
    }

    public record DeliveryOrderRecord(
            UUID tenantId,
            UUID orderId,
            String orderNo,
            String externalOrderNo,
            String orderStatus,
            String institutionName,
            String patientName,
            String receiverName,
            String receiverPhone,
            String receiverAddress,
            String addressType,
            Instant deliveryTime,
            Instant orderCreatedAt,
            String hospitalTypes
    ) {
    }

    public record ShipmentRecord(
            UUID shipmentId,
            UUID tenantId,
            UUID orderId,
            String orderNo,
            String externalOrderNo,
            Instant orderCreatedAt,
            String institutionName,
            String patientName,
            String receiverName,
            String receiverPhone,
            String receiverAddress,
            String addressType,
            Instant deliveryTime,
            String hospitalTypes,
            String logisticsNo,
            String logisticsCompany,
            String logisticsStatus,
            String payMethod,
            BigDecimal pkgWeight,
            Integer pkgNum,
            Instant packageTime,
            Instant outboundTime,
            Instant signTime,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ShipmentTraceRecord(
            UUID traceId,
            UUID tenantId,
            UUID shipmentId,
            UUID orderId,
            String logisticsNo,
            String traceStatus,
            String traceContent,
            String rawPayload,
            Instant traceTime,
            Instant createdAt
    ) {
    }

    public record LogisticsInfoRecord(
            UUID traceId,
            UUID tenantId,
            UUID shipmentId,
            UUID orderId,
            String orderNo,
            String externalOrderNo,
            String logisticsNo,
            String logisticsCompany,
            String operationInfo,
            String traceStatus,
            String receiverPhone,
            Instant traceTime,
            Instant createdAt
    ) {
    }
}
