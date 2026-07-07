package com.zhyf.decoction.application;

import java.time.Instant;
import java.util.UUID;

public final class DecoctionRecords {

    private DecoctionRecords() {
    }

    public record PdaLoginResult(
            String operator,
            String deviceCode,
            String token,
            Instant loginAt
    ) {
    }

    public record PrescriptionRecord(
            UUID tenantId,
            UUID orderId,
            UUID prescriptionId,
            String orderNo,
            String externalOrderNo,
            String prescriptionNo,
            String orderStatus
    ) {
    }

    public record DeviceRecord(
            String deviceCode,
            String deviceName,
            String deviceStatus,
            String activeTaskNo,
            String activePrescriptionNo
    ) {
    }

    public record DecoctionTaskRecord(
            UUID taskId,
            String taskNo,
            UUID tenantId,
            UUID orderId,
            UUID prescriptionId,
            String orderNo,
            String prescriptionNo,
            String deviceCode,
            String pailNo,
            String taskStatus,
            String operator,
            Instant startedAt,
            Instant finishedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record DecoctionTaskEventRecord(
            UUID eventId,
            UUID taskId,
            String taskNo,
            UUID tenantId,
            UUID orderId,
            String eventType,
            String operationId,
            String operator,
            String eventPayload,
            Instant eventTime,
            Instant createdAt
    ) {
    }

    public record DeviceWorkRecord(
            UUID recordId,
            UUID taskId,
            String taskNo,
            UUID tenantId,
            UUID orderId,
            String prescriptionNo,
            String deviceCode,
            String pailNo,
            String actionType,
            String actionResult,
            String taskStatusBefore,
            String taskStatusAfter,
            String operationId,
            String source,
            String operator,
            String detailPayload,
            Instant actionTime,
            Instant createdAt
    ) {
    }
}
