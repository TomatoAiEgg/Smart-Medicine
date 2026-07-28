package com.zhyf.message.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class SmsSendRecords {

    private SmsSendRecords() {
    }

    public record SmsSendCommand(
            UUID templateId,
            String receiverPhone,
            String receiverName,
            String relatedOrderNo,
            Map<String, String> variables,
            String operator
    ) {
    }

    public record SmsSendResult(
            UUID id,
            UUID tenantId,
            UUID templateId,
            String templateCode,
            String templateName,
            String receiverPhone,
            String receiverName,
            String relatedOrderNo,
            String signature,
            String content,
            String sendStatus,
            String providerMessageId,
            String failureReason,
            int retryCount,
            String operator,
            Instant createdAt,
            Instant updatedAt,
            Instant sentAt
    ) {
    }
}
