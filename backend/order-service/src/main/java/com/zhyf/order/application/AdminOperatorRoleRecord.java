package com.zhyf.order.application;

import java.time.Instant;

public record AdminOperatorRoleRecord(
        String roleCode,
        long operatorCount,
        long enabledCount,
        long disabledCount,
        Instant createdAt,
        Instant updatedAt
) {
}
