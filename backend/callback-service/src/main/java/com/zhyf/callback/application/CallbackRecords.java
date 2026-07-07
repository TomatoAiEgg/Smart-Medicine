package com.zhyf.callback.application;

import java.time.Instant;
import java.util.UUID;

public final class CallbackRecords {

    private CallbackRecords() {
    }

    public record CallbackRecord(
            UUID id,
            UUID tenantId,
            UUID orderId,
            String orderNo,
            String callbackType,
            String businessId,
            String requestUrl,
            String requestBody,
            String responseBody,
            String status,
            int retryCount,
            Instant nextRetryAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
