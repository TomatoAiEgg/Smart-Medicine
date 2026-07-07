package com.zhyf.workflow.domain;

import java.time.Instant;
import java.util.UUID;

public record WorkflowTaskSnapshot(
        UUID taskId,
        UUID tenantId,
        UUID orderId,
        String taskType,
        String taskStatus,
        String sourceEventId,
        String reviewer,
        String reviewComment,
        String orderNo,
        String externalOrderNo,
        String orderStatus,
        String validationStatus,
        String validationMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
}
