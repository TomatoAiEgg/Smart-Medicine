package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminOrderInterceptRuleRecord(
        UUID id,
        UUID tenantId,
        String ruleCode,
        String ruleName,
        String interceptStage,
        String matchField,
        String matchType,
        String matchValue,
        String reason,
        int priority,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
