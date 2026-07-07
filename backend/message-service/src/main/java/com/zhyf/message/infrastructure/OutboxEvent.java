package com.zhyf.message.infrastructure;

import java.util.UUID;

public record OutboxEvent(
        UUID id,
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        String payload
) {
}
