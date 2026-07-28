package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminInstitutionApiPermissionRecord(
        UUID id,
        UUID tenantId,
        UUID institutionId,
        String institutionCode,
        String institutionName,
        String institutionType,
        UUID apiId,
        String apiCode,
        String apiName,
        String requestMethod,
        String requestPath,
        boolean enabled,
        String remark,
        Instant createdAt,
        Instant updatedAt
) {
}
