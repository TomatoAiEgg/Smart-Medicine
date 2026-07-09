package com.zhyf.integration.application;

import java.time.Instant;
import java.util.UUID;

public final class IntegrationRecords {

    private IntegrationRecords() {
    }

    public record IntegrationMessageRecord(
            UUID messageId,
            String sourceType,
            String sourceSystem,
            String externalMessageId,
            String messageType,
            String businessKey,
            String processStatus,
            String normalizedPayload,
            String rawPayload,
            String failureReason,
            Instant createdAt,
            Instant updatedAt,
            Instant processedAt
    ) {
    }

    public record HospitalOrderRecord(
            UUID tenantId,
            UUID orderId,
            String institutionName,
            String orderNo,
            String externalOrderNo,
            String orderStatus,
            String prescriptionNo,
            String prescriptionStatus,
            String patientName,
            String receiverName,
            String receiverPhone,
            String receiverAddress,
            String logisticsNo,
            String logisticsCompany,
            String logisticsStatus,
            Instant createdAt
    ) {
    }

    public record IntegrationRetryTaskRecord(
            UUID taskId,
            UUID messageId,
            String taskType,
            String targetSystem,
            String businessKey,
            String requestUrl,
            String requestBody,
            String responseBody,
            String taskStatus,
            int retryCount,
            Instant nextRetryAt,
            Instant createdAt,
            Instant updatedAt,
            Instant processedAt
    ) {
    }
}
