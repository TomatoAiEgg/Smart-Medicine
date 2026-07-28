package com.zhyf.order.application;

import java.time.Instant;
import java.util.UUID;

public record AdminInstitutionApiRecord(
        UUID id,
        String apiCode,
        String apiName,
        String requestMethod,
        String requestPath,
        String description,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
