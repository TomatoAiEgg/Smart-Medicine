package com.zhyf.ops.application;

import java.time.Instant;
import java.util.UUID;

public final class OpsRecords {

    private OpsRecords() {
    }

    public record EventOutboxRecord(
            UUID id,
            UUID tenantId,
            String eventId,
            String eventType,
            String topic,
            String tag,
            String source,
            String aggregateType,
            String aggregateId,
            String status,
            int retryCount,
            int maxRetryCount,
            Instant nextRetryAt,
            String lastError,
            Instant createdAt,
            Instant updatedAt,
            Instant publishedAt
    ) {
    }

    public record MessageConsumeRecord(
            UUID id,
            String consumerGroup,
            String messageId,
            String eventId,
            String topic,
            String tag,
            String aggregateId,
            String status,
            int retryCount,
            String lastError,
            String traceEndpoint,
            Instant consumeStartedAt,
            Instant consumeFinishedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record DeadLetterRecord(
            UUID id,
            String eventId,
            String topic,
            String tag,
            String consumerGroup,
            String aggregateId,
            String errorMessage,
            int retryCount,
            String status,
            String operator,
            String remark,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record DeadLetterOperationResult(
            UUID id,
            String eventId,
            String status,
            int outboxResetCount,
            String message
    ) {
    }

    public record OrderValidationRecord(
            UUID id,
            UUID tenantId,
            UUID orderId,
            String eventId,
            String validationStatus,
            String validationMessage,
            Instant createdAt
    ) {
    }

    public record ApiAccessLogRecord(
            UUID id,
            UUID tenantId,
            UUID institutionId,
            String appKey,
            String requestPath,
            String requestIp,
            String resultCode,
            Instant createdAt
    ) {
    }

    public record LogisticsCallbackIssueRecord(
            UUID callbackId,
            UUID tenantId,
            UUID orderId,
            String orderNo,
            String callbackType,
            String businessId,
            String requestUrl,
            String responseBody,
            String callbackStatus,
            int retryCount,
            Instant nextRetryAt,
            Instant callbackCreatedAt,
            Instant callbackUpdatedAt,
            UUID shipmentId,
            String logisticsNo,
            String logisticsCompany,
            String logisticsStatus,
            String latestTraceStatus,
            String latestTraceContent,
            Instant latestTraceTime
    ) {
    }

    public record IntegrationRetryIssueRecord(
            UUID taskId,
            UUID messageId,
            String taskType,
            String targetSystem,
            String businessKey,
            String requestUrl,
            String responseBody,
            String taskStatus,
            int retryCount,
            Instant nextRetryAt,
            Instant taskCreatedAt,
            Instant taskUpdatedAt,
            Instant processedAt,
            String sourceType,
            String sourceSystem,
            String externalMessageId,
            String messageType,
            String processStatus,
            String failureReason
    ) {
    }

    public record OpsHealthOverview(
            int recentHours,
            long pendingOutbox,
            long failedOutbox,
            long failedConsumes,
            long rejectedValidations,
            long failedCallbacks,
            long deadCallbacks,
            long failedIntegrationRetries,
            long deadIntegrationRetries,
            long recentAccessCount
    ) {
    }
}
