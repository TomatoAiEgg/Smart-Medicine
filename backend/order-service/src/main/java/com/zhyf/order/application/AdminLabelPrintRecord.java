package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminLabelPrintRecord(
        UUID id,
        UUID tenantId,
        UUID orderId,
        UUID prescriptionId,
        String orderNo,
        String externalOrderNo,
        String prescriptionNo,
        String externalPrescriptionNo,
        String institutionName,
        String patientName,
        String printStatus,
        String printChannel,
        String printerCode,
        String printerName,
        String provider,
        String providerTaskNo,
        UUID templateId,
        String templateName,
        String requestParam,
        String responseBody,
        String failureReason,
        String operator,
        UUID retryOf,
        Instant createdAt,
        Instant updatedAt
) {
}
