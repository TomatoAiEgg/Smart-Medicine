package com.zhyf.message.application;

public record MessageEvent(
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        String messageId,
        String payload
) {
}
