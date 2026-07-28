package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminHerbIndexOperationLogRecord(
        UUID id,
        UUID tenantId,
        UUID indexId,
        UUID institutionId,
        String institutionCode,
        String institutionName,
        String externalHerbCode,
        String externalHerbName,
        UUID herbId,
        String herbCode,
        String herbName,
        String actionType,
        String operator,
        String remark,
        Instant createdAt
) {
}
