package com.zhyf.message.infrastructure;

import java.util.UUID;

public interface OrderValidationRecordRepository {

    void insert(
            UUID id,
            UUID tenantId,
            UUID orderId,
            String eventId,
            String validationStatus,
            String validationMessage,
            String rawPayload
    );
}
