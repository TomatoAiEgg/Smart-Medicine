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
            UUID deviceId,
            String deviceCode,
            String deviceName,
            String deviceType,
            String deviceGroup,
            String decoctionCenter,
            String pdaCode,
            String printerCode,
            String printTemplateCode,
            String deviceStatus,
            boolean enabled,
            String remark,
            String activeTaskNo,
            String activePrescriptionNo,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record WaterPailRecord(
            UUID pailId,
            String pailNo,
            String pailName,
            String decoctionCenter,
            String pailGroup,
            Integer capacityMl,
            String pailStatus,
            boolean enabled,
            String remark,
            String activeTaskNo,
            String activePrescriptionNo,
            Instant createdAt,
            Instant updatedAt
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
            Integer latestWaterVolumeMl,
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
