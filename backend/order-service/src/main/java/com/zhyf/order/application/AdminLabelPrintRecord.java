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
        UUID templateId,
        String templateName,
        String failureReason,
        String operator,
        UUID retryOf,
        Instant createdAt
) {
}
