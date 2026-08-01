package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminExportTaskRecord(
        UUID taskId,
        UUID tenantId,
        String taskType,
        String taskName,
        String taskStatus,
        String queryParam,
        String fileName,
        String contentType,
        Integer rowCount,
        Integer fileSizeBytes,
        String failureReason,
        String requestedBy,
        int retryCount,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
